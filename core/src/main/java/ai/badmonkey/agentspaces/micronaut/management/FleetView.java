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
package ai.badmonkey.agentspaces.micronaut.management;

import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.api.ad.AgentCard;
import ai.badmonkey.agentspaces.api.ad.AssetCard;
import ai.badmonkey.agentspaces.common.id.PeerId;
import ai.badmonkey.agentspaces.connect.ConnectorRuntime;
import ai.badmonkey.agentspaces.identity.PeerIdentity;
import ai.badmonkey.agentspaces.micronaut.FleetLifecycle;
import ai.badmonkey.agentspaces.micronaut.assets.AssetConnectors;
import ai.badmonkey.agentspaces.micronaut.enroll.FleetEnroller;
import ai.badmonkey.agentspaces.spring.AgentSpacesProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The one view the health indicator, info source, endpoint, and meters
 * share: this peer, its groups with member counts and spaces, the beans it
 * enrolled, the connectors it serves, and the cards it sees.
 */
@Singleton
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.micronaut.management.enabled", notEquals = "false")
public class FleetView {

    private final AgentSpaces spaces;
    private final PeerIdentity identity;
    private final AgentSpacesProperties properties;
    private final FleetLifecycle lifecycle;
    private final FleetEnroller enroller;
    private final AssetConnectors connectors;

    public FleetView(AgentSpaces spaces, PeerIdentity identity, AgentSpacesProperties properties,
                     FleetLifecycle lifecycle, FleetEnroller enroller,
                     @Nullable AssetConnectors connectors) {
        this.spaces = Objects.requireNonNull(spaces, "spaces");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.enroller = Objects.requireNonNull(enroller, "enroller");
        this.connectors = connectors;
    }

    public boolean running() {
        return lifecycle.isRunning();
    }

    public PeerId peerId() {
        return identity.peerId();
    }

    public int members(String groupName) {
        return spaces.group(groupName).runtime().membership().allMembers().size();
    }

    public int enrolledAgents() {
        return enroller.enrolled().size();
    }

    public int localAgentCards(String groupName) {
        PeerId self = identity.peerId();
        return spaces.group(groupName).discovery()
                .find(AgentCard.class, card -> self.equals(card.issuer())).size();
    }

    public int remoteAgentCards(String groupName) {
        PeerId self = identity.peerId();
        return spaces.group(groupName).discovery()
                .find(AgentCard.class, card -> !self.equals(card.issuer())).size();
    }

    public int assetCards(String groupName) {
        return spaces.group(groupName).discovery().find(AssetCard.class, card -> true).size();
    }

    /** The summary document: peer, profile, groups, enrollment, connectors. */
    public Map<String, Object> summary() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("peer", identity.peerId().value());
        view.put("running", running());
        view.put("profile", properties.getSecurity().getProfile());
        view.put("bind", properties.getBind());
        List<Map<String, Object>> groups = new ArrayList<>();
        for (String name : spaces.groupNames()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("name", name);
            group.put("id", spaces.group(name).id().value());
            group.put("members", members(name));
            group.put("spaces", new ArrayList<>(spaces.group(name).spaceNames()));
            group.put("local-agent-cards", localAgentCards(name));
            group.put("remote-agent-cards", remoteAgentCards(name));
            group.put("asset-cards", assetCards(name));
            groups.add(group);
        }
        view.put("groups", groups);
        view.put("enrolled-agents", enrolledAgents());
        Map<String, Object> served = new LinkedHashMap<>();
        if (connectors != null) {
            for (Map.Entry<String, ConnectorRuntime> entry : connectors.runtimes().entrySet()) {
                served.put(entry.getKey(), Map.of(
                        "executed", entry.getValue().executed(),
                        "served-from-space", entry.getValue().servedFromSpace(),
                        "materialized", entry.getValue().materialized()));
            }
        }
        view.put("connectors", served);
        return view;
    }

    /** The cards this group sees, agents and assets, as plain maps. */
    public Map<String, Object> cards() {
        Map<String, Object> all = new LinkedHashMap<>();
        for (String name : spaces.groupNames()) {
            List<Map<String, Object>> agents = new ArrayList<>();
            for (AgentCard card : spaces.group(name).discovery().find(AgentCard.class, c -> true)) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("agent", card.agent().localName());
                entry.put("issuer", card.issuer().value());
                entry.put("description", card.description());
                entry.put("goals", card.goals());
                entry.put("consumes", card.consumes());
                entry.put("produces", card.produces());
                entry.put("attested", card.attested());
                agents.add(entry);
            }
            List<Map<String, Object>> assets = new ArrayList<>();
            for (AssetCard card : spaces.group(name).discovery().find(AssetCard.class, c -> true)) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("asset", card.asset());
                entry.put("issuer", card.issuer().value());
                entry.put("uri", card.uri());
                entry.put("shape", card.shape());
                entry.put("freshness", card.freshness());
                entry.put("access", card.access());
                assets.add(entry);
            }
            all.put(name, Map.of("agents", agents, "assets", assets));
        }
        return all;
    }

    public List<String> groupNames() {
        return new ArrayList<>(spaces.groupNames());
    }
}
