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
package ai.badmonkey.agentspaces.micronaut.mcp;

import ai.badmonkey.agentspaces.agent.remote.RemoteAction;
import ai.badmonkey.agentspaces.agent.remote.RemoteActions;
import ai.badmonkey.agentspaces.micronaut.FleetContext;
import ai.badmonkey.agentspaces.micronaut.mcp.support.Contexts;
import ai.badmonkey.agentspaces.micronaut.tools.McpToolCall;
import ai.badmonkey.agentspaces.micronaut.tools.McpToolResult;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpImportTest {

    @Test
    @Timeout(180)
    void anExternalMcpServersToolBecomesAFleetActionAnyPeerCanInvoke() {
        int seedPort = Contexts.freePort();
        Map<String, Object> importer = new HashMap<>(Contexts.fleet(seedPort, 0));
        int httpPort = Contexts.freePort();
        importer.put("micronaut.server.port", String.valueOf(httpPort));
        importer.put("test.app", "importer");
        importer.put("micronaut.mcp.server.transport", "HTTP");
        importer.put("micronaut.mcp.server.info.name", "echo");
        importer.put("micronaut.mcp.server.info.version", "1");
        importer.put("micronaut.mcp.client.transport", "HTTP");
        importer.put("micronaut.mcp.client.http.echo.url", "http://localhost:" + httpPort + "/mcp");
        importer.put("agentspaces.micronaut.mcp.import.enabled", "true");
        importer.put("agentspaces.micronaut.mcp.import.servers[0]", "echo");   // not the auto-configured embeddedServer client
        importer.put("agentspaces.groups[0].spaces[3].name", "mcp-calls");
        Map<String, Object> caller = new HashMap<>(Contexts.fleet(Contexts.freePort(), seedPort));
        caller.put("agentspaces.groups[0].spaces[3].name", "mcp-calls");
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, importer);
        try (ApplicationContext importing = server.getApplicationContext();
             ApplicationContext calling = Contexts.run(caller, Map.of())) {
            McpFleetAgent agent = importing.getBean(McpFleetAgent.class);
            assertThat(Contexts.await(Duration.ofSeconds(60), () -> !agent.imported().isEmpty())).isTrue();
            assertThat(agent.imported()).containsOnlyKeys("echo");
            String key = "echo";
            assertThat(agent.imported().get(key)).contains("echo");

            FleetContext fleet = calling.getBean(FleetContext.class);
            RemoteActions remote = RemoteActions.over(fleet.group(), fleet.peerId());
            assertThat(Contexts.await(Duration.ofSeconds(60), () -> remote.available().stream()
                    .anyMatch(a -> a.inputType() == McpToolCall.class))).isTrue();
            List<RemoteAction> actions = remote.available().stream().filter(a -> a.inputType() == McpToolCall.class).toList();
            assertThat(actions).singleElement().satisfies(action -> {
                assertThat(action.card().agent().localName()).isEqualTo("mcp-" + key);
                assertThat(action.name()).contains("echo");
            });
            Object result = actions.get(0).invoke(new McpToolCall(key, "echo", "{\"text\":\"hi fleet\"}", "c1"),
                    Duration.ofSeconds(60)).orElseThrow();
            assertThat(result).isInstanceOf(McpToolResult.class);
            McpToolResult echoed = (McpToolResult) result;
            assertThat(echoed.error()).isFalse();
            assertThat(echoed.text()).contains("HI FLEET!");
            assertThat(echoed.callId()).isEqualTo("c1");
        }
    }
}
