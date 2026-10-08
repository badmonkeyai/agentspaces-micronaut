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

import ai.badmonkey.agentspaces.spring.AgentSpacesProperties;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FleetConfigurationTest {

    @Test
    void bindsTheSpringStarterPropertySetFromTheEnvironment() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
                "agentspaces.bind", "127.0.0.1:7500",
                "agentspaces.tick-millis", "200",
                "agentspaces.groups[0].name", "it-fleet",
                "agentspaces.groups[0].founding", "it-fleet-v1",
                "agentspaces.groups[0].seeds[0]", "hq.example:7500",
                "agentspaces.groups[0].spaces[0].name", "tasks",
                "agentspaces.groups[0].spaces[1].name", "findings",
                "agentspaces.groups[0].spaces[1].settle-window-millis", "100",
                "agentspaces.security.profile", "dev-local",
                "agentspaces.security.grants.raft-voter[0]", "peer:abc"))) {
            AgentSpacesProperties p = ctx.getBean(AgentSpacesProperties.class);
            assertThat(p.getBind()).isEqualTo("127.0.0.1:7500");
            assertThat(p.getTickMillis()).isEqualTo(200);
            assertThat(p.getGroups()).hasSize(1);
            AgentSpacesProperties.Group group = p.getGroups().get(0);
            assertThat(group.getName()).isEqualTo("it-fleet");
            assertThat(group.getFounding()).isEqualTo("it-fleet-v1");
            assertThat(group.getSeeds()).containsExactly("hq.example:7500");
            assertThat(group.getSpaces()).extracting(AgentSpacesProperties.SpaceDef::getName)
                    .containsExactly("tasks", "findings");
            assertThat(group.getSpaces().get(1).getSettleWindowMillis()).isEqualTo(100);
            assertThat(group.getSpaces().get(0).getSettleWindowMillis()).isEqualTo(200); // default kept
            assertThat(p.getSecurity().getProfile()).isEqualTo("dev-local");
            assertThat(p.getSecurity().getGrants().getRaftVoter()).containsExactly("peer:abc");
            // Untouched sections keep the POJO defaults.
            assertThat(p.getCardRefreshMillis()).isEqualTo(300_000);
            assertThat(p.getCapabilities().isVote()).isTrue();
        }
    }

    @Test
    void bindsDurationsInIsoAndSpringShortForms() {
        AgentSpacesProperties p = FleetConfiguration.bind(Map.of(
                "identity", Map.of("agent-certificate-ttl", "PT2H"),
                "transport", Map.of("tls", Map.of("crl-refresh", "90s")),
                "groups", List.of(Map.of("name", "g",
                        "content-key-rotation", Map.of("rotate-every", "1d", "writer-grace", "250ms")))));
        assertThat(p.getIdentity().getAgentCertificateTtl()).isEqualTo(Duration.ofHours(2));
        assertThat(p.getTransport().getTls().getCrlRefresh()).isEqualTo(Duration.ofSeconds(90));
        assertThat(p.getGroups().get(0).getContentKeyRotation().getRotateEvery())
                .isEqualTo(Duration.ofDays(1));
        assertThat(p.getGroups().get(0).getContentKeyRotation().getWriterGrace())
                .isEqualTo(Duration.ofMillis(250));
    }

    @Test
    void rejectsUnknownKeysSoTyposSurfaceAtStartup() {
        assertThatThrownBy(() -> FleetConfiguration.bind(Map.of("tick-milis", "5")))
                .hasMessageContaining("tick-milis");
    }

    @Test
    void anEmptySubtreeIsTheDefaults() {
        AgentSpacesProperties p = FleetConfiguration.bind(Map.of());
        assertThat(p.getGroups()).isEmpty();
        assertThat(p.getSecurity().getProfile()).isEqualTo("mtls");
    }
}
