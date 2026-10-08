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
package ai.badmonkey.agentspaces.examples.micronaut.ai;

import ai.badmonkey.agentspaces.micronaut.langchain4j.model.FleetChatModel;
import ai.badmonkey.agentspaces.micronaut.langchain4j.model.ModelServer;
import ai.badmonkey.agentspaces.micronaut.langchain4j.usage.FleetUsage;
import ai.badmonkey.agentspaces.micronaut.mcp.FleetMcpTools;
import ai.badmonkey.agentspaces.micronaut.tools.FleetActions;
import dev.langchain4j.model.chat.ChatModel;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** The three peers in one JVM over TCP: the whole loop of example 17 with scripted models. */
class MicronautAiFleetTest {

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean await(Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && !condition.getAsBoolean()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    @Test
    @Timeout(240)
    void theOrchestratorPlansWithFleetToolsAndTheFleetsModel() {
        int workerPort = freePort();
        try (ApplicationContext worker = ApplicationContext.run(Map.of(
                     "agentspaces.bind", "127.0.0.1:" + workerPort,
                     "test.scripted", "true",
                     "langchain4j.open-ai.enabled", "false"), "worker");
             ApplicationContext orchestrator = ApplicationContext.run(Map.of(
                     "agentspaces.bind", "127.0.0.1:" + freePort(),
                     "agentspaces.groups[0].seeds[0]", "127.0.0.1:" + workerPort,
                     "langchain4j.open-ai.enabled", "false"), "orchestrator")) {
            EmbeddedServer gatewayServer = ApplicationContext.run(EmbeddedServer.class, Map.of(
                    "micronaut.server.port", "-1",
                    "agentspaces.bind", "127.0.0.1:" + freePort(),
                    "agentspaces.groups[0].seeds[0]", "127.0.0.1:" + workerPort,
                    "langchain4j.open-ai.enabled", "false"), "gateway");
            try (ApplicationContext gateway = gatewayServer.getApplicationContext()) {
                assertThat(worker.getBean(ModelServer.class).isRunning()).isTrue();
                assertThat(orchestrator.getBean(ChatModel.class)).isInstanceOf(FleetChatModel.class);

                FleetActions tools = orchestrator.getBean(FleetActions.class);
                assertThat(await(Duration.ofSeconds(60), () -> tools.current().size() == 2)).isTrue();

                String plan = orchestrator.getBean(Orchestrator.class)
                        .plan("Plan a two-day offsite for 12 people.");
                assertThat(plan).startsWith("Plan:").contains("team retreats").contains("504.0");

                // Three orchestrator round-trips served through the space, plus the summarizer's own call.
                assertThat(worker.getBean(ScriptedModel.class).calls.get()).isEqualTo(4);
                assertThat(worker.getBean(ModelServer.class).served()).isEqualTo(3);
                assertThat(worker.getBean(FleetUsage.class).localByModel()).containsKey("gpt-4.1-mini");

                FleetMcpTools export = gateway.getBean(FleetMcpTools.class);
                assertThat(await(Duration.ofSeconds(60), () -> export.published().size() == 2)).isTrue();
                assertThat(export.published()).contains("fleet_summarizer_Brief", "fleet_pricer_PriceRequest");
            }
        }
    }
}
