/*
 * Copyright 2026 Bad Monkey, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.badmonkey.agentspaces.micronaut.pdt;

import ai.badmonkey.agentspaces.micronaut.MicronautFleetSettings;
import ai.badmonkey.agentspaces.pdt.NodeStore;
import ai.badmonkey.agentspaces.pdt.store.PackNodeStore;
import ai.badmonkey.agentspaces.peering.blocks.MemoryBlockStore;
import ai.badmonkey.agentspaces.spring.AgentSpacesProperties;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One {@link NodeStore} per group (srad/agentspaces-pdt.md PD-S-02, PD-C-03),
 * from {@code agentspaces.pdt.store.*} or from the application's beans. A
 * {@code NodeStore} bean named for a group ({@code @Named("order-fleet")}) is
 * that group's store; a sole unnamed bean is the fleet group's. A bean wins
 * over {@code store.kind}, and the ignored kind is logged (§7.2). Otherwise
 * {@code pack} opens pack files under {@code store.path/<group>},
 * {@code memory} keeps nodes on the heap, and {@code bean} insists on a bean.
 * A collector runs per group over its own live roots, so two groups never
 * share a store here: one collector would free what the other still holds.
 *
 * <p>The stores exist before the group's content swarm is built, because the
 * swarm serves and fetches trees through a tree store over the same
 * {@code NodeStore} ({@code Content.Builder.treeStore(PdtSwarm.treeStore(store))});
 * {@link Pdts} attaches each group's facade to that swarm.
 */
@Singleton
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.pdt.enabled", value = "true")
public class PdtStores implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PdtStores.class);

    private final PdtSettings settings;
    private final String fleetGroup;
    private final BeanContext context;
    private final InstantSource clock;
    private final Map<String, NodeStore> stores = new LinkedHashMap<>();
    private final Map<String, Boolean> fromBean = new LinkedHashMap<>();
    private final List<AutoCloseable> packs = new ArrayList<>();
    private boolean closed;

    public PdtStores(PdtSettings settings, MicronautFleetSettings fleet, AgentSpacesProperties properties,
                     BeanContext context, InstantSource clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.context = Objects.requireNonNull(context, "context");
        this.clock = Objects.requireNonNull(clock, "clock");
        String group = fleet.getGroup().isBlank()
                ? properties.getGroups().isEmpty() ? "" : properties.getGroups().get(0).getName()
                : fleet.getGroup().trim();
        this.fleetGroup = group;
    }

    /** The group the sole unnamed {@code NodeStore} bean belongs to. */
    public String fleetGroup() {
        return fleetGroup;
    }

    /**
     * The group's store, built on first use.
     *
     * @param group the group name
     * @return the store
     * @throws IllegalStateException when the configuration names a store this host cannot provide
     */
    public synchronized NodeStore storeFor(String group) {
        Objects.requireNonNull(group, "group");
        if (closed) {
            throw new IllegalStateException("the PDT stores are closed");
        }
        NodeStore store = stores.get(group);
        if (store == null) {
            store = build(group);
            stores.put(group, store);
        }
        return store;
    }

    /** Whether the group's store came from an application bean. */
    public synchronized boolean isBean(String group) {
        return fromBean.getOrDefault(group, false);
    }

    /** The groups whose stores exist. */
    public synchronized List<String> groups() {
        return List.copyOf(stores.keySet());
    }

    private NodeStore build(String group) {
        PdtSettings.Store config = settings.getStore();
        String kind = config.getKind();
        Optional<NodeStore> bean = context.findBean(NodeStore.class, Qualifiers.byName(group));
        if (bean.isEmpty() && group.equals(fleetGroup)) {
            bean = sole();
        }
        if (bean.isPresent()) {
            if (!PdtSettings.Store.BEAN.equals(kind)) {
                LOG.info("group {}: a NodeStore bean is the PDT store; agentspaces.pdt.store.kind={} is ignored",
                        group, kind);
            }
            fromBean.put(group, true);
            return bean.get();
        }
        fromBean.put(group, false);
        if (config.getQuota() > 0) {
            LOG.warn("group {}: agentspaces.pdt.store.quota={} is bound but the facade at this version applies no quota"
                    + " (PD-S-05 pending); the collector alone frees space", group, config.getQuota());
        }
        switch (kind) {
            case PdtSettings.Store.BEAN -> throw new IllegalStateException("agentspaces.pdt.store.kind=bean needs a"
                    + " NodeStore bean named '" + group + "'" + (group.equals(fleetGroup) ? " or one unnamed bean" : "")
                    + "; none is registered");
            case PdtSettings.Store.MEMORY -> {
                if (config.isRequire()) {
                    throw new IllegalStateException("agentspaces.pdt.store.require=true refuses a memory store;"
                            + " set store.kind=pack or give a durable NodeStore bean");
                }
                return new NodeStore(new MemoryBlockStore(), clock);
            }
            case PdtSettings.Store.PACK -> {
                if (config.getPath() == null || config.getPath().isBlank()) {
                    // PD-Q-AVL-02: no path means the memory store with a warning, unless the host requires a durable one
                    if (config.isRequire()) {
                        throw new IllegalStateException("agentspaces.pdt.store.require=true needs a durable store, and"
                                + " agentspaces.pdt.store.path is blank: set the path, or give a NodeStore bean");
                    }
                    LOG.warn("group {}: agentspaces.pdt.store.path is blank, so the trees live in memory and are lost on"
                            + " restart (set agentspaces.pdt.store.require=true to refuse this)", group);
                    return new NodeStore(new MemoryBlockStore(), clock);
                }
                Path dir = Path.of(config.getPath()).resolve(group.replaceAll("[^A-Za-z0-9._-]", "_"));
                try {
                    PackNodeStore pack = PackNodeStore.open(dir);
                    packs.add(pack);
                    return new NodeStore(pack, clock);
                } catch (IOException e) {
                    throw new UncheckedIOException("agentspaces.pdt.store.path: cannot use " + dir + ": "
                            + e.getMessage(), e);
                }
            }
            default -> throw new IllegalStateException("agentspaces.pdt.store.kind: unknown kind '" + kind
                    + "' (pack, memory, or bean)");
        }
    }

    private Optional<NodeStore> sole() {
        try {
            return context.findBean(NodeStore.class);
        } catch (NonUniqueBeanException several) {
            return Optional.empty();
        }
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (AutoCloseable pack : packs) {
            try {
                pack.close();
            } catch (Exception e) {
                LOG.warn("closing a PDT pack store failed", e);
            }
        }
        packs.clear();
    }
}
