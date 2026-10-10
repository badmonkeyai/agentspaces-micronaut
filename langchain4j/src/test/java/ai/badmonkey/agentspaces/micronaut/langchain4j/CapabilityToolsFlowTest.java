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

import ai.badmonkey.agentspaces.langchain4j.tools.FleetCapabilityTools;
import ai.badmonkey.agentspaces.langchain4j.tools.FleetToolProvider;
import ai.badmonkey.agentspaces.micronaut.langchain4j.support.Contexts;
import ai.badmonkey.agentspaces.micronaut.langchain4j.support.ScriptedChatModel;
import ai.badmonkey.agentspaces.micronaut.langchain4j.support.Voter;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.tool.ToolProviderRequest;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The capability tools on Micronaut: with {@code capability-tools.vote} and
 * {@code .aggregate} on, the provider offers the six tools to every
 * AiService, and a scripted model reads a decision through one of them.
 */
class CapabilityToolsFlowTest {

    @Test
    @Timeout(120)
    void anAiServiceReadsAFleetDecisionThroughTheCapabilityTools() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of(
                "test.app", "voter",
                "agentspaces.micronaut.langchain4j.capability-tools.vote", "true",
                "agentspaces.micronaut.langchain4j.capability-tools.aggregate", "true"))) {
            FleetCapabilityTools tools = ctx.getBean(FleetCapabilityTools.class);
            assertThat(ctx.getBean(FleetToolProvider.class)
                    .provideTools(new ToolProviderRequest("m", UserMessage.from("x"))).aiServiceTools())
                    .extracting(t -> t.toolSpecification().name())
                    .contains("propose_vote", "cast_ballot", "read_tally", "read_decision", "contribute", "read_estimate");

            // This peer's agent opens the vote and casts; a quorum of one closes it.
            assertThat(tools.proposeVote("plan", "Adopt the plan?", List.of("yes", "no"), 1)).contains("\"quorum\":1");
            assertThat(tools.castBallot("plan", "yes")).contains("\"cast\":\"yes\"");
            assertThat(Contexts.await(Duration.ofSeconds(30), () -> tools.readDecision("plan").contains("winner"))).isTrue();
            assertThat(tools.readDecision("plan")).contains("\"winner\":\"yes\"");

            // The model reads the decision through the tool and answers with it.
            String answer = ctx.getBean(Voter.class).ask("What was decided?");
            assertThat(answer).startsWith("decided: ").contains("\"winner\":\"yes\"");
            assertThat(ctx.getBean(ScriptedChatModel.class).requests()).hasSize(2);

            // The aggregate side: a contribution under an epoch, and its estimate.
            assertThat(tools.contribute("load", 10.0)).contains("10.0");
            assertThat(Contexts.await(Duration.ofSeconds(30), () -> tools.readEstimate("load").contains("\"estimate\""))).isTrue();
        }
    }

    @Test
    @Timeout(60)
    void onlyTheVoteToolsWhenOnlyVoteIsOn() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of(
                "agentspaces.micronaut.langchain4j.capability-tools.vote", "true"))) {
            assertThat(ctx.containsBean(FleetCapabilityTools.class)).isTrue();
            assertThat(ctx.getBean(FleetToolProvider.class)
                    .provideTools(new ToolProviderRequest("m", UserMessage.from("x"))).aiServiceTools())
                    .extracting(t -> t.toolSpecification().name())
                    .contains("propose_vote", "cast_ballot", "read_tally", "read_decision")
                    .doesNotContain("contribute", "read_estimate");
        }
    }

    @Test
    @Timeout(60)
    void theCapabilityToolsAreOffByDefault() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.app", "voter"))) {
            assertThat(ctx.containsBean(FleetCapabilityTools.class)).isFalse();
            assertThat(ctx.getBean(Voter.class).ask("anything")).isEqualTo("no capability tools offered");
        }
    }
}
