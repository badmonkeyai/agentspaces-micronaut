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

import ai.badmonkey.agentspaces.micronaut.mcp.support.Contexts;
import ai.badmonkey.agentspaces.micronaut.tools.FleetActions;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpExportTest {

    @Test
    @Timeout(180)
    void fleetToolsAppearOnTheMcpServerAndRunThroughTheFleet() throws Exception {
        int seedPort = Contexts.freePort();
        Map<String, Object> exporter = new HashMap<>(Contexts.fleet(Contexts.freePort(), seedPort));
        exporter.put("micronaut.server.port", "-1");
        exporter.put("micronaut.mcp.server.transport", "HTTP");
        exporter.put("micronaut.mcp.server.info.name", "fleet");
        exporter.put("micronaut.mcp.server.info.version", "0.2.0");
        exporter.put("agentspaces.micronaut.mcp.export.enabled", "true");
        exporter.put("agentspaces.micronaut.tools.refresh-interval", "500ms");
        try (ApplicationContext worker = Contexts.run(Contexts.fleet(seedPort, 0), Map.of("test.app", "worker"))) {
            EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, exporter);
            try (ApplicationContext ctx = server.getApplicationContext()) {
                FleetMcpTools export = ctx.getBean(FleetMcpTools.class);
                assertThat(Contexts.await(Duration.ofSeconds(60), () -> export.published().contains("fleet_summarizer_Brief")))
                        .isTrue();
                McpStatelessSyncServer mcp = ctx.getBean(McpStatelessSyncServer.class);
                assertThat(mcp.listTools()).extracting(McpSchema.Tool::name).contains("fleet_summarizer_Brief");

                // The handler runs the tool through the fleet: the worker on the other peer answers.
                var tool = ctx.getBean(FleetActions.class).current().get(0);
                McpSchema.CallToolResult result = export.handler(tool).apply(
                        new McpSchema.CallToolRequest("fleet_summarizer_Brief", Map.of("topic", "offsite", "sentences", 2)));
                assertThat(result.isError()).isFalse();
                assertThat(result.content()).singleElement().satisfies(content ->
                        assertThat(((McpSchema.TextContent) content).text()).contains("summary of offsite in 2 sentences"));
                assertThat(McpTransportContext.EMPTY).isNotNull();

                // And an MCP client over HTTP lists it.
                HttpResponse<String> listed = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                                URI.create(server.getURI() + "/mcp"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertThat(listed.body()).contains("fleet_summarizer_Brief");
            }
        }
    }

    @Test
    void theExportIsOffByDefault() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of())) {
            assertThat(ctx.containsBean(FleetMcpTools.class)).isFalse();
        }
    }
}
