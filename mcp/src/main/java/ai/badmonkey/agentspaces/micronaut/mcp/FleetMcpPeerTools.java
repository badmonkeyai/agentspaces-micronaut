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

import ai.badmonkey.agentspaces.langchain4j.tools.FleetCapabilityTools;
import ai.badmonkey.agentspaces.langchain4j.tools.FleetDiscoveryTools;
import ai.badmonkey.agentspaces.langchain4j.tools.JsonSchemas;
import ai.badmonkey.agentspaces.micronaut.FleetContext;
import ai.badmonkey.agentspaces.micronaut.FleetParticipant;
import ai.badmonkey.agentspaces.micronaut.tools.FleetActions;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.ToolExecutor;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * This peer's own tools through micronaut-mcp's server: the capability
 * tools ({@code export.capability-tools.vote}, {@code .aggregate}) and the
 * discovery tools ({@code export.discovery-tools.agents}, {@code .assets},
 * {@code .data}) of {@code agentspaces-langchain4j}, each exported as an MCP
 * tool whose input schema is the {@code @Tool} method's. Unlike the
 * catalogue's remote actions ({@link FleetMcpTools}), these never change
 * while the peer runs, so they are added on start and removed on stop. An
 * MCP client calling them acts as this peer's agent: its ballot is that
 * agent's record, its contribution joins the group's epoch.
 */
@Singleton
@Order(310)
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.micronaut.mcp.export.enabled", value = "true")
@Requires(classes = {McpStatelessSyncServer.class, FleetCapabilityTools.class})
@Requires(condition = FleetMcpPeerTools.PeerToolsOn.class)
public class FleetMcpPeerTools implements FleetParticipant {

    private static final Logger LOG = LoggerFactory.getLogger(FleetMcpPeerTools.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final FleetContext fleet;
    private final McpSettings.Export export;
    private final FleetActions actions;
    private final BeanProvider<McpSyncServer> syncServer;
    private final BeanProvider<McpStatelessSyncServer> statelessServer;
    private final Map<String, Function<McpSchema.CallToolRequest, McpSchema.CallToolResult>> published = new LinkedHashMap<>();

    public FleetMcpPeerTools(FleetContext fleet, McpSettings settings, @Nullable FleetActions actions,
                             BeanProvider<McpSyncServer> syncServer,
                             BeanProvider<McpStatelessSyncServer> statelessServer) {
        this.fleet = Objects.requireNonNull(fleet, "fleet");
        this.export = settings.getExport();
        this.actions = actions;
        this.syncServer = syncServer;
        this.statelessServer = statelessServer;
    }

    @Override
    public synchronized void start() {
        for (Owner owner : owners()) {
            for (ToolSpecification spec : ToolSpecifications.toolSpecificationsFrom(owner.target())) {
                if (owner.enabled().test(spec.name())) {
                    add(spec, owner.executor().apply(spec.name()));
                }
            }
        }
    }

    @Override
    public synchronized void stop() {
        for (String name : List.copyOf(published.keySet())) {
            remove(name);
        }
    }

    /** The tool names currently published. */
    public synchronized Set<String> published() {
        return Set.copyOf(published.keySet());
    }

    /** The handler of a published tool, for callers that invoke without a transport. */
    public synchronized Function<McpSchema.CallToolRequest, McpSchema.CallToolResult> handler(String name) {
        Function<McpSchema.CallToolRequest, McpSchema.CallToolResult> handler = published.get(name);
        if (handler == null) {
            throw new IllegalArgumentException("no exported peer tool named " + name + "; published: " + published.keySet());
        }
        return handler;
    }

    /** The MCP tool for a LangChain4j tool specification. */
    static McpSchema.Tool tool(ToolSpecification spec) {
        Map<String, Object> schema = spec.parameters() == null
                ? Map.of("type", "object", "properties", Map.of())
                : JsonSchemas.toMap(spec.parameters());
        return McpSchema.Tool.builder().name(spec.name()).description(spec.description()).inputSchema(schema).build();
    }

    /** The handler of one tool: arguments to JSON, execute, text or error. */
    static Function<McpSchema.CallToolRequest, McpSchema.CallToolResult> handler(String name, ToolExecutor executor) {
        return request -> {
            try {
                String arguments = JSON.writeValueAsString(request.arguments() == null ? Map.of() : request.arguments());
                String text = executor.execute(ToolExecutionRequest.builder().id(name).name(name).arguments(arguments).build(), null);
                return McpSchema.CallToolResult.builder().addTextContent(text == null ? "" : text).isError(false).build();
            } catch (JacksonException | RuntimeException e) {
                LOG.debug("peer tool {} failed", name, e);
                return McpSchema.CallToolResult.builder().addTextContent(String.valueOf(e.getMessage())).isError(true).build();
            }
        };
    }

    private List<Owner> owners() {
        List<Owner> owners = new ArrayList<>();
        McpSettings.Export.CapabilityTools capability = export.getCapabilityTools();
        if (capability.any()) {
            FleetCapabilityTools tools = new FleetCapabilityTools(fleet, new FleetCapabilityTools.Settings(
                    capability.isVote(), capability.isAggregate(), capability.getBallotLease(), capability.getSettleTimeout()));
            owners.add(new Owner(tools, tools::enabled, tools::executor));
        }
        McpSettings.Export.DiscoveryTools discovery = export.getDiscoveryTools();
        if (discovery.any()) {
            FleetDiscoveryTools tools = new FleetDiscoveryTools(fleet,
                    new ai.badmonkey.agentspaces.langchain4j.LangChain4jSettings.DiscoveryTools(
                            discovery.isAgents(), discovery.isAssets(), discovery.isData(), discovery.getDataSpace(),
                            discovery.getTimeout(), discovery.getMaxResultChars()),
                    actions);
            owners.add(new Owner(tools, tools::enabled, tools::executor));
        }
        return owners;
    }

    private void add(ToolSpecification spec, ToolExecutor executor) {
        McpSchema.Tool mcpTool = tool(spec);
        Function<McpSchema.CallToolRequest, McpSchema.CallToolResult> handler = handler(spec.name(), executor);
        boolean added = false;
        try {
            if (statelessServer.isPresent()) {
                statelessServer.get().addTool(new McpStatelessServerFeatures.SyncToolSpecification(mcpTool,
                        (context, request) -> handler.apply(request)));
                added = true;
            }
            if (syncServer.isPresent()) {
                syncServer.get().addTool(new McpServerFeatures.SyncToolSpecification(mcpTool,
                        (exchange, request) -> handler.apply(request)));
                added = true;
            }
        } catch (RuntimeException e) {
            LOG.warn("exporting peer tool {} to the MCP server failed: {}", spec.name(), e.toString());
            return;
        }
        if (added) {
            published.put(spec.name(), handler);
            LOG.info("exported peer tool {} to the MCP server", spec.name());
        } else {
            LOG.warn("no MCP server bean to export {} to: set micronaut.mcp.server.transport", spec.name());
        }
    }

    private void remove(String name) {
        try {
            if (statelessServer.isPresent()) {
                statelessServer.get().removeTool(name);
            }
            if (syncServer.isPresent()) {
                syncServer.get().removeTool(name);
            }
        } catch (RuntimeException e) {
            LOG.debug("removing {} failed", name, e);
        }
        published.remove(name);
    }

    /** One object carrying {@code @Tool} methods, with its own switch and executor lookup. */
    private record Owner(Object target, Predicate<String> enabled, Function<String, ToolExecutor> executor) {
    }

    /** Any capability-tools or discovery-tools flag on under the export. */
    public static final class PeerToolsOn implements Condition {
        private static final String PREFIX = "agentspaces.micronaut.mcp.export.";
        private static final List<String> FLAGS = List.of(
                "capability-tools.vote", "capability-tools.aggregate",
                "discovery-tools.agents", "discovery-tools.assets", "discovery-tools.data");

        @Override
        public boolean matches(ConditionContext context) {
            for (String flag : FLAGS) {
                if (context.getProperty(PREFIX + flag, Boolean.class).orElse(false)) {
                    return true;
                }
            }
            return false;
        }
    }
}
