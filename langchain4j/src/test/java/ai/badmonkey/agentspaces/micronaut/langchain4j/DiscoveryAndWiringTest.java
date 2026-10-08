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
package ai.badmonkey.agentspaces.micronaut.langchain4j;

import ai.badmonkey.agentspaces.api.spi.Embedder;
import ai.badmonkey.agentspaces.micronaut.langchain4j.embed.LangChain4jEmbedder;
import ai.badmonkey.agentspaces.micronaut.langchain4j.support.Contexts;
import ai.badmonkey.agentspaces.micronaut.langchain4j.support.CustomizedOrchestrator;
import ai.badmonkey.agentspaces.micronaut.langchain4j.support.Orchestrator;
import ai.badmonkey.agentspaces.micronaut.langchain4j.tools.FleetDiscoveryTools;
import ai.badmonkey.agentspaces.micronaut.langchain4j.tools.FleetToolProvider;
import ai.badmonkey.agentspaces.micronaut.langchain4j.worker.ProviderBuilderListeners;
import ai.badmonkey.agentspaces.micronaut.tools.FleetActions;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.data.message.UserMessage;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryAndWiringTest {

    @Test
    @Timeout(180)
    void theDiscoveryToolsFindAgentsAndAssetsAndReadDataAcrossTheFleet() {
        int seedPort = Contexts.freePort();
        try (ApplicationContext worker = Contexts.run(Contexts.fleet(seedPort, 0), Map.of("test.app", "worker"));
             ApplicationContext analyst = Contexts.run(Contexts.fleet(Contexts.freePort(), seedPort), Map.of(
                     "agentspaces.micronaut.langchain4j.discovery-tools.agents", "true",
                     "agentspaces.micronaut.langchain4j.discovery-tools.assets", "true",
                     "agentspaces.micronaut.langchain4j.discovery-tools.data", "true",
                     "agentspaces.micronaut.langchain4j.discovery-tools.timeout", "5s",
                     "agentspaces.micronaut.tools.refresh-interval", "500ms"))) {
            FleetActions actions = analyst.getBean(FleetActions.class);
            assertThat(Contexts.await(Duration.ofSeconds(30), () -> !actions.current().isEmpty())).isTrue();
            FleetDiscoveryTools tools = analyst.getBean(FleetDiscoveryTools.class);

            assertThat(Contexts.await(Duration.ofSeconds(30), () ->
                    tools.findFleetAgents("summarize a topic", 5).contains("summarizer"))).isTrue();
            assertThat(tools.findFleetAgents("summarize a topic", 5))
                    .contains("\"tools\":[\"fleet_summarizer_Brief\"]").contains("\"consumes\":[\"Brief\"]");
            assertThat(Contexts.await(Duration.ofSeconds(30), () ->
                    tools.listFleetAssets("customer orders", 5).contains("\"asset\":\"orders\""))).isTrue();
            assertThat(tools.fetchFleetData("orders", Map.of())).contains("\"sku\":\"widget\"").contains("\"fromCache\":false");

            // All three ride along in the provider's result.
            var provided = analyst.getBean(FleetToolProvider.class)
                    .provideTools(new ToolProviderRequest("m", UserMessage.from("x")));
            assertThat(provided.aiServiceTools()).extracting(t -> t.toolSpecification().name())
                    .contains("fleet_summarizer_Brief", "find_fleet_agents", "list_fleet_assets", "fetch_fleet_data");
        }
    }

    @Test
    @Timeout(60)
    void theCustomizerOptsOneServiceIntoTheFleetWhenAutoApplyIsOff() {
        try (ApplicationContext orchestrator = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.app", "orchestrator", "agentspaces.micronaut.langchain4j.tools.auto-apply", "false"))) {
            // The plain service sees no fleet tools; the customized one does (and so its model receives the
            // tool specifications, even though no worker is on this fleet to answer).
            assertThat(orchestrator.getBean(Orchestrator.class).plan("anything")).isEqualTo("no fleet tools offered");
            assertThat(orchestrator.getBean(CustomizedOrchestrator.class)).isNotNull();
            assertThat(orchestrator.getBean(ProviderBuilderListeners.Ollama.class)).isNotNull();
            assertThat(orchestrator.getBean(ProviderBuilderListeners.Anthropic.class)).isNotNull();
        }
    }

    @Test
    void theLangChain4jEmbedderReplacesTheHashingDefaultWhenAsked() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of(
                "test.embedding-model", "true",
                "agentspaces.micronaut.langchain4j.embedder.type", "langchain4j",
                "agentspaces.micronaut.langchain4j.embedder.model", "scripted"))) {
            Embedder embedder = ctx.getBean(Embedder.class);
            assertThat(embedder).isInstanceOf(LangChain4jEmbedder.class);
            assertThat(embedder.identity()).isEqualTo("langchain4j:scripted");
            assertThat(embedder.dimensions()).isEqualTo(2);
        }
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of("test.embedding-model", "true"))) {
            assertThat(ctx.getBean(Embedder.class)).isNotInstanceOf(LangChain4jEmbedder.class);
        }
    }
}
