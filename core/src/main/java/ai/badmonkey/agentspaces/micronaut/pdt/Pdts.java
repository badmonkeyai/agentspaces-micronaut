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

import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.agent.pdt.PRef;
import ai.badmonkey.agentspaces.api.space.Space;
import ai.badmonkey.agentspaces.common.crypto.GroupKeyRing;
import ai.badmonkey.agentspaces.content.Content;
import ai.badmonkey.agentspaces.pdt.NodeStore;
import ai.badmonkey.agentspaces.pdt.Pdt;
import ai.badmonkey.agentspaces.pdt.swarm.NodeStoreTreeStore;
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
 * The {@link Pdt} facades of this peer, one per group, built from
 * {@link PdtSettings} (srad/agentspaces-pdt.md §7.2, PD-C-03). Each facade
 * attaches to its group's content swarm ({@code group.provider(Content.TYPE)}),
 * whose tree store must be the adapter over the group's {@link NodeStore} from
 * {@link PdtStores}; it reads its heads from the group's registered
 * {@code agentspaces.pdt.space}, so the space's own admission decides who may
 * write a head (PD-P-04). {@link #start} starts every facade and joins its
 * tick to the group runtime's tick, the clock the swarm already ticks on
 * (QA3 A3-1); {@link #close} unhooks and closes them.
 */
public final class Pdts implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Pdts.class);

    /** One group's facade and its parts. */
    public record Member(String group, Pdt pdt, NodeStore store, Space refSpace, Content content) {
    }

    private final Map<String, Member> members = new LinkedHashMap<>();
    private final List<AutoCloseable> tickHooks = new ArrayList<>();
    private final AgentSpaces spaces;
    private boolean started;
    private boolean closed;

    /**
     * Builds a facade for every group that has a content swarm.
     *
     * @param spaces   the fabric, its groups built
     * @param stores   the node store per group
     * @param settings the keys
     * @param refs     the per-ref modes
     * @param clock    the clock of leases and retention
     * @throws IllegalStateException when a group lacks the content swarm, the ref space, or a swarm tree store
     *                               over the group's node store
     */
    public Pdts(AgentSpaces spaces, PdtStores stores, PdtSettings settings, List<RefSettings> refs,
                InstantSource clock) {
        this(spaces, stores, settings, refs, clock, null, null);
    }

    /**
     * As {@link #Pdts(AgentSpaces, PdtStores, PdtSettings, List, InstantSource)} with the challenge deadline of
     * {@code agentspaces.store.challenge-deadline} (PDT 2 phase 3, P2-S-07); each facade also finds the content
     * stores' cards through its group's discovery (P2-S-11).
     *
     * @param challengeDeadline the deadline, or null for the facade's default
     * @param holder            the shared wiring's holder, which adopts each facade so the spaces under
     *                          {@code agentspaces.pdt.spaces} and the {@code @PdtRef} sites reach it (PDT 2 phase 7,
     *                          P2-X-03, P2-X-04), or null when the fabric was wired without one
     */
    public Pdts(AgentSpaces spaces, PdtStores stores, PdtSettings settings, List<RefSettings> refs,
                InstantSource clock, java.time.Duration challengeDeadline, ai.badmonkey.agentspaces.spring.Pdts holder) {
        this.spaces = Objects.requireNonNull(spaces, "spaces");
        Objects.requireNonNull(stores, "stores");
        Objects.requireNonNull(settings, "settings");
        for (String groupName : spaces.groupNames()) {
            AgentSpaces.GroupContext group = spaces.group(groupName);
            Member member = build(group, stores.storeFor(groupName), settings, refs, clock, challengeDeadline);
            members.put(groupName, member);
            if (holder != null) {
                holder.adopt(groupName, member.pdt());
            }
        }
    }

    static Member build(AgentSpaces.GroupContext group, NodeStore store, PdtSettings settings,
                        List<RefSettings> refs, InstantSource clock, java.time.Duration challengeDeadline) {
        String name = group.name();
        Content content = group.provider(Content.TYPE)
                .filter(Content.class::isInstance).map(Content.class::cast)
                .orElseThrow(() -> new IllegalStateException("agentspaces.pdt.enabled=true needs"
                        + " agentspaces.content.enabled=true: PDT rides the content swarm, and group '" + name
                        + "' has none (srad/agentspaces-pdt.md §7.2)"));
        if (!group.spaceNames().contains(settings.getSpace())) {
            throw new IllegalStateException("agentspaces.pdt.space=" + settings.getSpace() + " is not a registered"
                    + " space of group '" + name + "' (it has " + group.spaceNames() + "); add it to the group's"
                    + " spaces so its admission governs the ref heads (PD-P-04)");
        }
        Space refSpace = group.space(settings.getSpace());
        NodeStoreTreeStore adapter = content.treeStore()
                .filter(NodeStoreTreeStore.class::isInstance).map(NodeStoreTreeStore.class::cast)
                .orElseThrow(() -> new IllegalStateException("group '" + name + "': the content swarm carries no"
                        + " tree store, so PDT cannot attach to it: the swarm must be built with"
                        + " Content.Builder.treeStore(PdtSwarm.treeStore(store)) over the group's NodeStore"
                        + " (srad/agentspaces-pdt.md §7.2, PD-C-02); the shared wiring in"
                        + " agentspaces-spring-boot-autoconfigure 0.5.0 builds the swarm without one"));
        if (adapter.store() != store) {
            throw new IllegalStateException("group '" + name + "': the content swarm's tree store is over another"
                    + " NodeStore than the one agentspaces.pdt.store (or the NodeStore bean) names; build the swarm"
                    + " over the group's PDT store (srad/agentspaces-pdt.md §7.2)");
        }
        // PD-P-05, PD-C-03: the sealed-nodes mode seals under the group's content-key ring, which
        // the swarm carries; a group without a content key names sealing=none, as the Spring
        // starter does, since a sealed facade over an empty ring could write nothing.
        String sealing = settings.getSealing();
        Optional<GroupKeyRing> ring = content.keyRing().filter(r -> !r.epochs().isEmpty());
        if (PRef.SEALING_SEALED_NODES.equals(sealing) && ring.isPresent()
                && !(store instanceof ai.badmonkey.agentspaces.pdt.seal.SealedNodeStore)) {
            // PDT 2 phase 7, P2-X-04: PdtStores.treeStore sealed the store the swarm was built over, which the facade
            // adopts; a swarm over an unsealed store could carry nothing this facade seals
            throw new IllegalStateException("group '" + name + "': agentspaces.pdt.sealing=sealed-nodes needs the"
                    + " content swarm built over the sealed store PdtStores.treeStore gives, and it was built over "
                    + store);
        }
        if (ring.isEmpty() && !PRef.SEALING_NONE.equals(sealing)) {
            LOG.warn("group {}: agentspaces.pdt.sealing={} but the group has no content-key, so its heads name"
                    + " sealing=none", name, sealing);
            sealing = PRef.SEALING_NONE;
        }
        Pdt.Builder builder = Pdt.builder(refSpace)
                .store(store)
                .clock(clock)
                .retain(settings.getRetain().getVersions(), settings.getRetain().getAge())
                .loadTimeout(settings.getLoadTimeout())
                .inlineDelta(settings.getInlineDelta())
                .follow(settings.getFollow())
                .sealing(sealing)
                .headLease(settings.getHeadLease())
                .cancelGrace(settings.getCancelGrace())
                .collectEvery(settings.getCollectEvery())
                .take(settings.getTake().getLease(), settings.getTake().getTimeout(), settings.getTake().getRetries());
        ring.ifPresent(builder::keyRing);
        // PDT 2 follow-ups, 2026-11-09, F1: the peer signs the pins Pdt.storeFor writes
        builder.identity(group.identity());
        if (group.discovery() != null) {
            builder.discovery(group.discovery());
        }
        if (challengeDeadline != null) {
            builder.challengeDeadline(challengeDeadline);
        }
        for (RefSettings ref : refs == null ? List.<RefSettings>of() : refs) {
            if (ref.getMode() != null) {
                if (!PRef.MODE_MULTI.equals(ref.getMode()) && !PRef.MODE_LINEAR.equals(ref.getMode())) {
                    throw new IllegalStateException("agentspaces.pdt.refs." + ref.getName() + ".mode=" + ref.getMode()
                            + " is not multi or linear");
                }
                builder.mode(ref.getName(), ref.getMode());
            }
            if (ref.getFollow() != null) {
                // PDT 2 phase 7, P2-X-08: the ref's own follow
                if (!Pdt.FOLLOW_FULL.equals(ref.getFollow()) && !Pdt.FOLLOW_LAZY.equals(ref.getFollow())) {
                    throw new IllegalStateException("agentspaces.pdt.refs." + ref.getName() + ".follow=" + ref.getFollow()
                            + " is not full or lazy");
                }
                builder.follow(ref.getName(), ref.getFollow());
            }
        }
        Pdt pdt = builder.build().attach(content);
        LOG.info("group {}: PDT over space '{}', store {}, follow {}, inline delta {} bytes", name,
                settings.getSpace(), settings.getStore().getKind(), settings.getFollow(), settings.getInlineDelta());
        return new Member(name, pdt, store, refSpace, content);
    }

    /** The group's facade. */
    public Pdt pdt(String group) {
        return find(group).orElseThrow(() -> new IllegalStateException("no PDT for group '" + group + "'; it has "
                + members.keySet()));
    }

    /** The group's facade, when the group has one. */
    public Optional<Pdt> find(String group) {
        Member member = members.get(group);
        return member == null ? Optional.empty() : Optional.of(member.pdt());
    }

    /** The groups with a facade. */
    public List<String> groups() {
        return List.copyOf(members.keySet());
    }

    /** Every member. */
    public List<Member> members() {
        return List.copyOf(members.values());
    }

    /** Starts every facade and joins each to its group runtime's tick; idempotent. */
    public synchronized void start() {
        if (started || closed) {
            return;
        }
        started = true;
        for (Member member : members.values()) {
            member.pdt().start();
            AgentSpaces.GroupContext group = spaces.group(member.group());
            if (group.runtime() != null) {
                tickHooks.add(group.runtime().onTick(member.pdt()::tick));
            }
        }
    }

    /** Whether {@link #start} ran. */
    public synchronized boolean started() {
        return started;
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
                LOG.warn("unhooking a PDT tick failed", e);
            }
        }
        tickHooks.clear();
        for (Member member : members.values()) {
            member.pdt().close();
        }
    }
}
