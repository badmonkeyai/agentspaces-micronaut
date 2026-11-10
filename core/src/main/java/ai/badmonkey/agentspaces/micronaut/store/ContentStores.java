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
package ai.badmonkey.agentspaces.micronaut.store;

import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.identity.PeerIdentity;
import ai.badmonkey.agentspaces.micronaut.pdt.Pdts;
import ai.badmonkey.agentspaces.spring.AgentSpacesProperties;
import ai.badmonkey.agentspaces.spring.Authorizers;
import ai.badmonkey.agentspaces.store.ContentStore;
import ai.badmonkey.agentspaces.store.StorePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The content stores of this peer under Micronaut, one per group with a facade (PDT 2 phase 3, P2-S-10;
 * srad/agentspaces-pdt2.md §7.2): built from the same {@code agentspaces.store.*} keys the Spring starter reads,
 * through {@code ai.badmonkey.agentspaces.spring.ContentStores.policy}, over each group's {@code Pdt} facade from
 * {@link Pdts}. The shared wiring already built each group's swarm at feature level 2. {@link #start} starts
 * every store and joins its tick to its group's runtime; {@link #close} closes them, which withdraws their cards.
 */
public final class ContentStores implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ContentStores.class);

    private final Map<String, ContentStore> byGroup = new LinkedHashMap<>();
    private final List<AutoCloseable> tickHooks = new ArrayList<>();
    private final AgentSpaces spaces;
    private boolean started;
    private boolean closed;

    /**
     * Builds a store over every group's facade.
     *
     * @param spaces      the fabric
     * @param pdts        the facades
     * @param properties  the shared keys
     * @param identity    this peer
     * @param authorizers the groups' authorizers
     * @param policy      a {@code StorePolicy} bean, or null to read the keys
     * @param clock       the clock
     */
    public ContentStores(AgentSpaces spaces, Pdts pdts, AgentSpacesProperties properties, PeerIdentity identity,
                         Authorizers authorizers, StorePolicy policy, InstantSource clock) {
        this.spaces = Objects.requireNonNull(spaces, "spaces");
        AgentSpacesProperties.StorePeer config = properties.getStore();
        StorePolicy chosen = policy != null ? policy : ai.badmonkey.agentspaces.spring.ContentStores.policy(config);
        for (Pdts.Member member : pdts.members()) {
            ContentStore.Builder builder = ContentStore.builder(member.pdt(), spaces.group(member.group()).discovery(), identity)
                    .policy(chosen).clock(clock).authorizer(authorizers.forGroup(member.group()));
            if (config.getName() != null && !config.getName().isBlank()) {
                builder.name(config.getName().trim());
            }
            byGroup.put(member.group(), builder.build());
        }
    }

    /** Starts every store and joins its tick to its group runtime's tick; idempotent. */
    public synchronized void start() {
        if (started || closed) {
            return;
        }
        started = true;
        for (Map.Entry<String, ContentStore> e : byGroup.entrySet()) {
            e.getValue().start();
            AgentSpaces.GroupContext group = spaces.group(e.getKey());
            if (group.runtime() != null) {
                tickHooks.add(group.runtime().onTick(e.getValue()::tick));
            }
            LOG.info("group {}: content store {} over a capacity of {} bytes", e.getKey(), e.getValue().assetName(),
                    e.getValue().policy().capacityBytes());
        }
    }

    /** The group's store, when the group has one. */
    public Optional<ContentStore> find(String group) {
        return Optional.ofNullable(byGroup.get(group));
    }

    /** The group's store. */
    public ContentStore store(String group) {
        return find(group).orElseThrow(() -> new IllegalStateException("no content store for group '" + group
                + "'; it has " + byGroup.keySet()));
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (AutoCloseable hook : tickHooks) {
            try {
                hook.close();
            } catch (Exception e) {
                LOG.warn("unhooking a store tick failed", e);
            }
        }
        byGroup.values().forEach(ContentStore::close);
    }
}
