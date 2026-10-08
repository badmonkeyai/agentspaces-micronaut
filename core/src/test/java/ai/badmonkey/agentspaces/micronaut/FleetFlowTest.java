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
import ai.badmonkey.agentspaces.api.ad.CapabilityAdvertisement;
import ai.badmonkey.agentspaces.api.space.Lease;
import ai.badmonkey.agentspaces.api.space.Template;
import ai.badmonkey.agentspaces.identity.PeerIdentity;
import ai.badmonkey.agentspaces.micronaut.enroll.FleetEnroller;
import ai.badmonkey.agentspaces.micronaut.support.Contexts;
import ai.badmonkey.agentspaces.micronaut.support.ItFinding;
import ai.badmonkey.agentspaces.micronaut.support.ItTask;
import ai.badmonkey.agentspaces.micronaut.support.PlainSpecAgent;
import ai.badmonkey.agentspaces.micronaut.support.Researcher;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FleetFlowTest {

    @Test
    @Timeout(120)
    void theStereotypeBeanEnrollsAndWorksTheFleet() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.app", "researcher"))) {
            AgentSpaces spaces = ctx.getBean(AgentSpaces.class);
            assertThat(ctx.getBean(FleetEnroller.class).enrolled()).hasSize(1);

            spaces.group("it-fleet").space("tasks")
                    .write(new ItTask("micronaut wiring", 1), Lease.of(Duration.ofMinutes(10)));

            assertThat(spaces.group("it-fleet").space("findings")
                    .read(Template.of(ItFinding.class), Duration.ofSeconds(20)))
                    .hasValueSatisfying(f -> assertThat(f.summary()).isEqualTo("done: micronaut wiring"));
            assertThat(ctx.getBean(Researcher.class).worked).containsExactly("micronaut wiring");

            assertThat(spaces.group("it-fleet").discovery().find(AgentCard.class,
                    card -> "it-researcher".equals(card.agent().localName()))).hasSize(1);
            assertThat(spaces.group("it-fleet").discovery().find(CapabilityAdvertisement.class, ad -> true))
                    .isNotEmpty()
                    .allSatisfy(ad -> assertThat(ad.issuer()).isEqualTo(ctx.getBean(PeerIdentity.class).peerId()))
                    .extracting(CapabilityAdvertisement::capabilityType)
                    .contains("aspace:cap/vote", "aspace:cap/aggregate");
        }
    }

    @Test
    @Timeout(60)
    void aPlainSingletonWithTheCoreAnnotationEnrollsToo() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.app", "plain-spec"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled())
                    .singleElement().isInstanceOf(PlainSpecAgent.class);
            AgentSpaces spaces = ctx.getBean(AgentSpaces.class);
            spaces.group("it-fleet").space("tasks")
                    .write(new ItTask("plain", 1), Lease.of(Duration.ofMinutes(10)));
            assertThat(spaces.group("it-fleet").space("findings")
                    .read(Template.of(ItFinding.class), Duration.ofSeconds(20)))
                    .hasValueSatisfying(f -> assertThat(f.summary()).isEqualTo("plain: plain"));
        }
    }

    @Test
    @Timeout(60)
    void aBeanNamingAnUnregisteredSpaceFailsAtStartup() {
        assertThatThrownBy(() -> Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.app", "misconfigured")).close())
                .hasMessageContaining("nowhere")
                .hasMessageContaining("it-fleet");
    }

    @Test
    @Timeout(60)
    void anAopInterceptedAgentFailsAtStartupWithTheReason() {
        assertThatThrownBy(() -> Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.app", "intercepted")).close())
                .hasMessageContaining("intercepted by Micronaut AOP")
                .hasMessageContaining("@Retryable");
    }

    @Test
    @Timeout(180)
    void twoContextsFormOneFleetOverTcp() {
        int seedPort = Contexts.freePort();
        try (ApplicationContext first = Contexts.run(Contexts.fleet(seedPort, 0), Map.of());
             ApplicationContext second = Contexts.run(Contexts.fleet(Contexts.freePort(), seedPort),
                     Map.of("test.app", "researcher"))) {
            AgentSpaces writer = first.getBean(AgentSpaces.class);
            writer.group("it-fleet").space("tasks")
                    .write(new ItTask("cross-context", 2), Lease.of(Duration.ofMinutes(10)));
            assertThat(writer.group("it-fleet").space("findings")
                    .read(Template.of(ItFinding.class), Duration.ofSeconds(60)))
                    .hasValueSatisfying(f -> assertThat(f.summary()).isEqualTo("done: cross-context"));
            assertThat(second.getBean(Researcher.class).worked).containsExactly("cross-context");
        }
    }
}
