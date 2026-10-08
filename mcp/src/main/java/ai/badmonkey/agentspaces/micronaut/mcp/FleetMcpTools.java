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

import ai.badmonkey.agentspaces.micronaut.FleetParticipant;
import ai.badmonkey.agentspaces.micronaut.tools.FleetActions;
import ai.badmonkey.agentspaces.micronaut.tools.FleetTool;
import ai.badmonkey.agentspaces.micronaut.tools.FleetToolFilter;
import ai.badmonkey.agentspaces.micronaut.tools.FleetToolsChangedEvent;
import ai.badmonkey.agentspaces.micronaut.tools.ToolInvocation;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.runtime.event.annotation.EventListener;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The fleet's tools through micronaut-mcp's server: on start and on every
 * {@link FleetToolsChangedEvent}, the tools the export allows (its own
 * include and exclude lists, since an MCP client is outside the fleet's trust
 * boundary) are added to, and lapsed ones removed from, whichever server the
 * transport produced: {@code McpSyncServer} over STDIO (which notifies clients
 * when {@code micronaut.mcp.server.tools.list-changed} is on) or
 * {@code McpStatelessSyncServer} over HTTP (clients see the change on their
 * next {@code tools/list}; the stateless transport has no notifications).
 */
@Singleton
@Order(300)
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.micronaut.mcp.export.enabled", value = "true")
@Requires(classes = McpStatelessSyncServer.class)
@Requires(beans = FleetActions.class)
public class FleetMcpTools implements FleetParticipant {

    private static final Logger LOG = LoggerFactory.getLogger(FleetMcpTools.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final FleetActions actions;
    private final FleetToolFilter exportFilter;
    private final BeanProvider<McpSyncServer> syncServer;
    private final BeanProvider<McpStatelessSyncServer> statelessServer;
    private final Map<String, FleetTool> published = new LinkedHashMap<>();
    private boolean started;

    public FleetMcpTools(FleetActions actions, McpSettings settings,
                         BeanProvider<McpSyncServer> syncServer,
                         BeanProvider<McpStatelessSyncServer> statelessServer) {
        this.actions = Objects.requireNonNull(actions, "actions");
        McpSettings.Export export = settings.getExport();
        this.exportFilter = FleetToolFilter.of(export.getIncludeAgents(), export.getExcludeAgents(), false);
        this.syncServer = syncServer;
        this.statelessServer = statelessServer;
    }

    @Override
    public synchronized void start() {
        started = true;
        LOG.debug("MCP export started: stateless server present={}, sync server present={}",
                statelessServer.isPresent(), syncServer.isPresent());
        sync(actions.current());
    }

    @EventListener
    public synchronized void onFleetToolsChanged(FleetToolsChangedEvent event) {
        LOG.debug("MCP export sees a tool change (started={}): +{} -{}", started, event.added(), event.removed());
        if (started) {
            sync(event.current());
        }
    }

    @Override
    public synchronized void stop() {
        started = false;
        for (String name : List.copyOf(published.keySet())) {
            remove(name);
        }
    }

    /** The tool names currently published. */
    public synchronized Set<String> published() {
        return Set.copyOf(published.keySet());
    }

    /** The MCP tool for a fleet tool, with the handler that runs it. */
    public McpSchema.Tool tool(FleetTool tool) {
        return McpSchema.Tool.builder().name(tool.name()).description(tool.description())
                .inputSchema(tool.inputSchema()).build();
    }

    /** The handler of one fleet tool: arguments to JSON, invoke, text or error. */
    public Function<McpSchema.CallToolRequest, McpSchema.CallToolResult> handler(FleetTool tool) {
        ToolInvocation invocation = actions.invocation();
        return request -> {
            try {
                String arguments = JSON.writeValueAsString(request.arguments() == null ? Map.of() : request.arguments());
                ToolInvocation.Outcome outcome = invocation.call(tool.action(), arguments);
                return McpSchema.CallToolResult.builder().addTextContent(outcome.text()).isError(outcome.error()).build();
            } catch (ToolInvocation.ToolInvocationException | JacksonException e) {
                return McpSchema.CallToolResult.builder().addTextContent(e.getMessage()).isError(true).build();
            }
        };
    }

    private void sync(List<FleetTool> current) {
        Map<String, FleetTool> allowed = new LinkedHashMap<>();
        for (FleetTool tool : current) {
            if (exportFilter.include(tool.action())) {
                allowed.put(tool.name(), tool);
            }
        }
        for (String name : List.copyOf(published.keySet())) {
            if (!allowed.containsKey(name)) {
                remove(name);
            }
        }
        for (FleetTool tool : allowed.values()) {
            if (!published.containsKey(tool.name())) {
                add(tool);
            }
        }
    }

    private void add(FleetTool tool) {
        McpSchema.Tool mcpTool = tool(tool);
        Function<McpSchema.CallToolRequest, McpSchema.CallToolResult> handler = handler(tool);
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
            LOG.warn("exporting fleet tool {} to the MCP server failed: {}", tool.name(), e.toString());
            return;
        }
        if (added) {
            published.put(tool.name(), tool);
            LOG.info("exported fleet tool {} to the MCP server", tool.name());
        } else {
            LOG.warn("no MCP server bean to export {} to: set micronaut.mcp.server.transport", tool.name());
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
}
