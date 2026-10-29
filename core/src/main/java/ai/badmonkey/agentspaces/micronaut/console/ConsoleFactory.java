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
package ai.badmonkey.agentspaces.micronaut.console;

import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.console.ConsoleCommand;
import ai.badmonkey.agentspaces.console.ConsolePanel;
import ai.badmonkey.agentspaces.console.ConsoleView;
import ai.badmonkey.agentspaces.console.ContentPanel;
import ai.badmonkey.agentspaces.console.FleetCommander;
import ai.badmonkey.agentspaces.console.FleetConsoleServer;
import ai.badmonkey.agentspaces.peering.node.PeerNode;
import ai.badmonkey.agentspaces.spring.AgentSpacesConsoleAutoConfiguration;
import ai.badmonkey.agentspaces.spring.AgentSpacesProperties;
import ai.badmonkey.agentspaces.spring.ConsoleViewCustomizer;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * The fleet console ({@code agentspaces.console.enabled=true}) built by the
 * Spring starter's console build methods, with the application's
 * {@link ConsoleViewCustomizer}, {@link ConsoleCommand}, and
 * {@link ConsolePanel} beans; {@link ConsoleLifecycle} serves it on
 * {@code agentspaces.console.port}.
 */
@Factory
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.console.enabled", value = "true")
public class ConsoleFactory {

    private final AgentSpacesConsoleAutoConfiguration wiring = new AgentSpacesConsoleAutoConfiguration();

    @Singleton
    @Requires(missingBeans = ConsoleView.class)
    public ConsoleView consoleView(AgentSpaces spaces, PeerNode node,
                                   List<ConsoleViewCustomizer> customizers) {
        return wiring.agentSpacesConsoleView(spaces, node, customizers);
    }

    @Singleton
    @Requires(missingBeans = FleetCommander.class)
    @Requires(property = "agentspaces.console.command.enabled", value = "true")
    public FleetCommander fleetCommander(AgentSpacesProperties properties, AgentSpaces spaces,
                                         ConsoleView view, List<ConsoleCommand> commands) {
        return wiring.agentSpacesCommander(properties, spaces, view, commands);
    }

    /** The Content panel (srad/large-networks-bulk-data.md LB-O-03) when the content swarm is on. */
    @Singleton
    @Requires(missingBeans = ContentPanel.class)
    @Requires(property = "agentspaces.content.enabled", value = "true")
    public ContentPanel contentPanel(PeerNode node, AgentSpaces spaces) {
        return wiring.agentSpacesContentPanel(node, spaces);
    }

    @Singleton
    @Requires(missingBeans = FleetConsoleServer.class)
    public FleetConsoleServer consoleServer(AgentSpacesProperties properties, ConsoleView view,
                                            List<ConsolePanel> panels, List<FleetCommander> commanders) {
        return wiring.agentSpacesConsoleServer(properties, view, panels, commanders);
    }
}
