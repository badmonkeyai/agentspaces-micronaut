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

import ai.badmonkey.agentspaces.agent.AgentBinder;
import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.api.ad.AgentCard;
import ai.badmonkey.agentspaces.api.ad.CardAction;
import ai.badmonkey.agentspaces.api.space.Lease;
import ai.badmonkey.agentspaces.api.space.Space;
import ai.badmonkey.agentspaces.api.space.TakenEntry;
import ai.badmonkey.agentspaces.api.space.Template;
import ai.badmonkey.agentspaces.common.id.AgentId;
import ai.badmonkey.agentspaces.micronaut.FleetContext;
import ai.badmonkey.agentspaces.micronaut.FleetParticipant;
import ai.badmonkey.agentspaces.micronaut.tools.McpToolCall;
import ai.badmonkey.agentspaces.micronaut.tools.McpToolResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static ai.badmonkey.agentspaces.api.space.Matchers.eq;

/**
 * External MCP servers as fleet agents: for every LangChain4j {@code McpClient}
 * bean micronaut-mcp-client-langchain4j creates (or the subset in
 * {@code import.servers}), the server's tools become one AgentCard, agent
 * {@code mcp-<key>}, with one {@code take} action per tool consuming
 * {@link McpToolCall} and producing {@link McpToolResult}; a worker loop takes
 * calls for that server from the import space, runs the tool through the
 * client, and completes the take with the result. Any peer on the fleet,
 * whatever its framework, invokes the tool through the space, and only this
 * peer holds the connection and credentials. Tools are re-listed on each card
 * refresh, and a server that is not up at startup is retried.
 */
@Singleton
@Order(310)
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.micronaut.mcp.import.enabled", value = "true")
@Requires(classes = McpClient.class)
public class McpFleetAgent implements FleetParticipant {

    private static final Logger LOG = LoggerFactory.getLogger(McpFleetAgent.class);
    private static final String CALL_SCHEMA = McpToolCall.class.getName() + "#v1";
    private static final String RESULT_SCHEMA = McpToolResult.class.getName() + "#v1";

    private final FleetContext fleet;
    private final McpSettings.Import settings;
    private final BeanContext beans;
    private final TaskScheduler scheduler;
    private final Map<String, Imported> imported = new LinkedHashMap<>();
    private Space space;
    private ScheduledFuture<?> retry;
    private volatile boolean running;

    public McpFleetAgent(FleetContext fleet, McpSettings settings, BeanContext beans,
                         @Named(TaskExecutors.SCHEDULED) TaskScheduler scheduler) {
        this.fleet = Objects.requireNonNull(fleet, "fleet");
        this.settings = settings.getImport();
        this.beans = Objects.requireNonNull(beans, "beans");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * The client beans, resolved when needed: micronaut-mcp's LangChain4j
     * client connects as it is created, so a server that is not up yet would
     * fail this bean's creation if the clients were injected; resolving them
     * on the retry schedule lets the import wait for the server instead.
     */
    private List<McpClient> clients() {
        try {
            return List.copyOf(beans.getBeansOfType(McpClient.class));
        } catch (RuntimeException e) {
            LOG.info("MCP clients not ready yet: {}", e.getMessage() == null ? e.toString()
                    : e.getMessage().lines().findFirst().orElse(e.toString()));
            return List.of();
        }
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        space = fleet.requiredSpace(settings.getSpace(), "agentspaces.micronaut.mcp.import.space");
        retry = scheduler.scheduleWithFixedDelay(Duration.ofSeconds(1), Duration.ofSeconds(5), this::importAll);
    }

    @Override
    public synchronized void refresh() {
        if (running) {
            importAll();
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (retry != null) {
            retry.cancel(false);
            retry = null;
        }
        for (Imported entry : imported.values()) {
            entry.close();
        }
        imported.clear();
    }

    /** The servers imported so far, by client key, with their tool names. */
    public synchronized Map<String, List<String>> imported() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        imported.forEach((key, entry) -> out.put(key, entry.toolNames));
        return out;
    }

    private synchronized void importAll() {
        if (!running) {
            return;
        }
        for (McpClient client : clients()) {
            String key = client.key();
            if (!settings.getServers().isEmpty() && !settings.getServers().contains(key)) {
                continue;
            }
            List<ToolSpecification> tools;
            try {
                tools = client.listTools();
            } catch (RuntimeException e) {
                LOG.info("MCP server '{}' not listable yet: {}", key, e.toString());
                continue;
            }
            List<String> names = tools.stream().map(ToolSpecification::name).toList();
            Imported current = imported.get(key);
            if (current != null && current.toolNames.equals(names)) {
                continue;
            }
            if (current != null) {
                current.close();
            }
            imported.put(key, publish(client, tools));
            LOG.info("imported MCP server '{}' as fleet agent mcp-{} with tools {}", key, key, names);
        }
    }

    private Imported publish(McpClient client, List<ToolSpecification> tools) {
        AgentSpaces.GroupContext group = fleet.group();
        String key = client.key();
        AgentId agent = new AgentId(fleet.peerId(), "mcp-" + key);
        List<CardAction> actions = new ArrayList<>();
        List<String> goals = new ArrayList<>();
        for (ToolSpecification tool : tools) {
            String description = tool.description() == null ? "" : tool.description();
            actions.add(new CardAction(tool.name(), description.length() > CardAction.MAX_DESCRIPTION_LENGTH
                    ? description.substring(0, CardAction.MAX_DESCRIPTION_LENGTH) : description,
                    List.of(CALL_SCHEMA), List.of(RESULT_SCHEMA), settings.getSpace(), CardAction.TAKE));
            goals.add(tool.name());
        }
        String instructions = client.instructions() == null ? "" : client.instructions().strip();
        String description = "MCP server '" + key + "'" + (instructions.isEmpty() ? "" : ": " + instructions)
                + ". Call a tool by writing an McpToolCall(server, tool, argumentsJson, callId).";
        if (description.length() > 500) {
            description = description.substring(0, 500);
        }
        AgentCard card = new AgentCard("aspace://" + group.id().value() + "/agent/" + fleet.peerId().value()
                + "/" + agent.localName(), fleet.peerId(), group.id(), group.clock().instant(), Duration.ofMinutes(15),
                agent, description, goals, List.of(CALL_SCHEMA), List.of(RESULT_SCHEMA), Map.of(),
                Map.of(CALL_SCHEMA, settings.getSpace()), null, actions);
        AgentBinder.Bound bound = group.binder().adopt(card);
        Thread worker = Thread.ofVirtual().name("mcp-import-" + key).start(() -> loop(client));
        return new Imported(tools.stream().map(ToolSpecification::name).toList(), bound, worker);
    }

    private void loop(McpClient client) {
        String key = client.key();
        Template<McpToolCall> calls = Template.of(McpToolCall.class).where("server", eq(key));
        while (running && !Thread.currentThread().isInterrupted()) {
            Optional<TakenEntry<McpToolCall>> taken;
            try {
                taken = space.take(calls, Lease.of(settings.getLease()), Duration.ofSeconds(1));
            } catch (RuntimeException e) {
                if (!running) {
                    return;
                }
                LOG.warn("mcp-import '{}': take failed", key, e);
                continue;
            }
            taken.ifPresent(entry -> serve(client, entry));
        }
    }

    private void serve(McpClient client, TakenEntry<McpToolCall> taken) {
        McpToolCall call = taken.entry();
        McpToolResult result;
        try {
            ToolExecutionResult executed = client.executeTool(ToolExecutionRequest.builder()
                    .id(call.callId()).name(call.tool()).arguments(call.argumentsJson() == null ? "{}" : call.argumentsJson())
                    .build());
            result = new McpToolResult(call.server(), call.tool(), call.callId(), executed.resultText(),
                    Boolean.TRUE.equals(executed.isError()));
        } catch (RuntimeException e) {
            result = new McpToolResult(call.server(), call.tool(), call.callId(), e.getMessage(), true);
        }
        space.complete(taken, result, Lease.of(settings.getResultLease()));
    }

    /** One imported server: its tools, the adopted card, and the worker. */
    private record Imported(List<String> toolNames, AgentBinder.Bound bound, Thread worker) {
        void close() {
            worker.interrupt();
            bound.close();
        }
    }
}
