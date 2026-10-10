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
package ai.badmonkey.agentspaces.micronaut.support;

import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.capabilities.orderedlog.OrderedTakes;
import ai.badmonkey.agentspaces.common.codec.CborCodec;
import ai.badmonkey.agentspaces.common.id.PeerId;
import ai.badmonkey.agentspaces.identity.PeerIdentity;
import ai.badmonkey.agentspaces.space.replicated.ReplicatedSpace;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.InstantSource;
import java.util.Arrays;
import java.util.List;

/**
 * Registers an ordered-log coordinator over the tasks space, as the Spring
 * starter asks an application to do: the Raft log runs over the fixed member
 * set named by {@code test.ordered.members} (the same list on every peer),
 * and the clerk depends on this bean so the coordinator exists before the
 * clerk enrolls. A log of one member never elects a leader (a candidate is
 * promoted only on a vote reply), so the test runs two peers.
 */
@Singleton
@Requires(property = "test.app", value = "ordered-clerk")
public class OrderedWiring {

    private final OrderedTakes ordered;

    public OrderedWiring(AgentSpaces spaces, PeerIdentity identity, InstantSource clock,
                         @Property(name = "test.ordered.members") String members,
                         @Property(name = "test.ordered.seed") long seed) {
        AgentSpaces.GroupContext group = spaces.group("it-fleet");
        ReplicatedSpace tasks = (ReplicatedSpace) group.space("tasks");
        List<PeerId> memberIds = Arrays.stream(members.split(",")).map(String::trim).map(PeerId::of).toList();
        this.ordered = OrderedTakes.over(group.pipes(), CborCodec.defaultCodec(), identity, "app",
                memberIds, clock, seed, tasks);
        group.ordered("tasks", ordered);
    }

    public OrderedTakes ordered() {
        return ordered;
    }
}
