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
package ai.badmonkey.agentspaces.micronaut;

import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.api.ad.AgentCard;
import ai.badmonkey.agentspaces.api.space.Lease;
import ai.badmonkey.agentspaces.api.space.Template;
import ai.badmonkey.agentspaces.micronaut.enroll.FleetEnroller;
import ai.badmonkey.agentspaces.micronaut.support.Contexts;
import ai.badmonkey.agentspaces.micronaut.support.EstimateOnlyAgent;
import ai.badmonkey.agentspaces.micronaut.support.ItFinding;
import ai.badmonkey.agentspaces.micronaut.support.ItTally;
import ai.badmonkey.agentspaces.micronaut.support.ItTask;
import ai.badmonkey.agentspaces.micronaut.support.JoinOnlyAgent;
import ai.badmonkey.agentspaces.micronaut.support.ProposeOnlyAgent;
import ai.badmonkey.agentspaces.micronaut.support.ReduceOnlyAgent;
import ai.badmonkey.agentspaces.micronaut.support.Tallier;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The 0.3.0 method annotations under compile-time enrollment: this module's
 * tests compile with {@code agentspaces-micronaut-processor}, so a plain
 * {@code @Singleton} whose only fleet annotation is {@code @SpaceJoin},
 * {@code @Propose}, {@code @OnEstimate}, or {@code @SpaceReduce} is marked
 * {@code @FleetBound} and enrolled by {@code FleetMethodProcessor}, and a
 * {@code @SpaceReduce} on a stereotype bean enrolls the same way.
 */
class CompileTimeEnrollTest {

    @Test
    @Timeout(60)
    void aPlainSingletonWithOnlyASpaceJoinEnrollsAndJoins() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.app", "join-only"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled())
                    .singleElement().isInstanceOf(JoinOnlyAgent.class);
            AgentSpaces spaces = ctx.getBean(AgentSpaces.class);
            spaces.group("it-fleet").space("tasks").write(new ItTask("gather", 1), Lease.of(Duration.ofMinutes(10)));
            spaces.group("it-fleet").space("tasks").write(new ItTask("gather", 2), Lease.of(Duration.ofMinutes(10)));
            assertThat(spaces.group("it-fleet").space("findings")
                    .read(Template.of(ItFinding.class), Duration.ofSeconds(20)))
                    .hasValueSatisfying(f -> {
                        assertThat(f.topic()).isEqualTo("gather");
                        assertThat(f.summary()).isEqualTo("joined: 2");
                    });
        }
    }

    @Test
    @Timeout(60)
    void aPlainSingletonWithOnlyAProposeEnrolls() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.app", "propose-only"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled())
                    .singleElement().isInstanceOf(ProposeOnlyAgent.class);
            assertThat(ctx.getBean(AgentSpaces.class).group("it-fleet").discovery().find(AgentCard.class,
                    card -> card.actions().stream().anyMatch(action -> "open".equals(action.name()))))
                    .hasSize(1);
        }
    }

    @Test
    @Timeout(60)
    void aPlainSingletonWithOnlyAnOnEstimateEnrolls() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.app", "estimate-only"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled())
                    .singleElement().isInstanceOf(EstimateOnlyAgent.class);
            assertThat(ctx.getBean(AgentSpaces.class).group("it-fleet").discovery().find(AgentCard.class,
                    card -> card.actions().stream().anyMatch(action -> "report".equals(action.name()))))
                    .hasSize(1);
        }
    }

    @Test
    @Timeout(60)
    void aStereotypeBeanWithASpaceReduceEnrollsAndFolds() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.app", "tallier"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled())
                    .singleElement().isInstanceOf(Tallier.class);
            AgentSpaces spaces = ctx.getBean(AgentSpaces.class);
            spaces.group("it-fleet").space("tasks").write(new ItTask("fold", 1), Lease.of(Duration.ofMinutes(10)));
            spaces.group("it-fleet").space("tasks").write(new ItTask("fold", 2), Lease.of(Duration.ofMinutes(10)));
            assertThat(Contexts.await(Duration.ofSeconds(20), () -> spaces.group("it-fleet").space("tasks")
                    .readAll(Template.of(ItTally.class), 10).stream().anyMatch(t -> t.points() == 3))).isTrue();
        }
    }

    @Test
    @Timeout(60)
    void aPlainSingletonWithOnlyASpaceReduceEnrollsAndFolds() throws Exception {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.app", "reduce-only"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled())
                    .singleElement().isInstanceOf(ReduceOnlyAgent.class);
            AgentSpaces spaces = ctx.getBean(AgentSpaces.class);
            spaces.group("it-fleet").space("tasks").write(new ItTask("fold", 1), Lease.of(Duration.ofMinutes(10)));
            spaces.group("it-fleet").space("tasks").write(new ItTask("fold", 2), Lease.of(Duration.ofMinutes(10)));
            assertThat(Contexts.await(Duration.ofSeconds(20), () -> spaces.group("it-fleet").space("tasks")
                    .readAll(Template.of(ItTally.class), 10).stream().anyMatch(t -> t.points() == 3))).isTrue();
        }
    }

    @Test
    @Timeout(60)
    void aReduceNamingAnUnregisteredSpaceFailsNamingItAndTheGroups() {
        assertThatThrownBy(() -> Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.app", "misconfigured-tallier")).close())
                .hasStackTraceContaining("[nowhere]")
                .hasStackTraceContaining("it-fleet");
    }
}
