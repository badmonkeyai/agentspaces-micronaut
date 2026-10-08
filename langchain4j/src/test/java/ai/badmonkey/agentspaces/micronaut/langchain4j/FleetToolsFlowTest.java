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

import ai.badmonkey.agentspaces.micronaut.langchain4j.support.Contexts;
import ai.badmonkey.agentspaces.micronaut.langchain4j.support.Orchestrator;
import ai.badmonkey.agentspaces.micronaut.langchain4j.support.ScriptedChatModel;
import ai.badmonkey.agentspaces.micronaut.langchain4j.tools.FleetToolProvider;
import ai.badmonkey.agentspaces.micronaut.tools.FleetActions;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FleetToolsFlowTest {

    @Test
    @Timeout(180)
    void anAiServiceCallsAWorkerOnAnotherPeerAsATool() {
        int seedPort = Contexts.freePort();
        try (ApplicationContext worker = Contexts.run(Contexts.fleet(seedPort, 0), Map.of("test.app", "worker"));
             ApplicationContext orchestrator = Contexts.run(Contexts.fleet(Contexts.freePort(), seedPort),
                     Map.of("test.app", "orchestrator", "agentspaces.micronaut.tools.refresh-interval", "500ms"))) {
            FleetActions actions = orchestrator.getBean(FleetActions.class);
            assertThat(Contexts.await(Duration.ofSeconds(30), () -> !actions.current().isEmpty())).isTrue();
            assertThat(actions.current()).extracting("name").containsExactly("fleet_summarizer_Brief");
            assertThat(orchestrator.getBean(FleetToolProvider.class).specifications())
                    .singleElement().satisfies(spec -> {
                        assertThat(spec.description()).contains("Summarizes a topic").contains("Brief");
                        assertThat(spec.parameters().properties()).containsKeys("topic", "sentences");
                        assertThat(spec.parameters().required()).containsExactly("topic", "sentences");
                    });

            String plan = orchestrator.getBean(Orchestrator.class).plan("Plan the offsite");
            assertThat(plan).contains("summary of offsite in 2 sentences");
            assertThat(orchestrator.getBean(ScriptedChatModel.class).requests()).hasSize(2);
            // The model saw the fleet tool on its first round-trip and the tool's result on its second.
            assertThat(orchestrator.getBean(ScriptedChatModel.class).requests().get(0).toolSpecifications())
                    .extracting("name").contains("fleet_summarizer_Brief");
        }
    }

    @Test
    @Timeout(60)
    void autoApplyOffLeavesServicesWithoutFleetTools() {
        try (ApplicationContext orchestrator = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.app", "orchestrator", "agentspaces.micronaut.langchain4j.tools.auto-apply", "false"))) {
            assertThat(orchestrator.getBean(Orchestrator.class).plan("anything")).isEqualTo("no fleet tools offered");
        }
    }

    @Test
    void theProviderBacksOffWhenDisabled() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("agentspaces.micronaut.langchain4j.tools.enabled", "false"))) {
            assertThat(ctx.containsBean(FleetToolProvider.class)).isFalse();
            assertThat(ctx.containsBean(FleetActions.class)).isTrue();
        }
    }
}
