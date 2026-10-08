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
import ai.badmonkey.agentspaces.api.spi.Authorizer;
import ai.badmonkey.agentspaces.api.spi.Embedder;
import ai.badmonkey.agentspaces.micronaut.support.Contexts;
import ai.badmonkey.agentspaces.micronaut.support.CustomEmbedder;
import ai.badmonkey.agentspaces.peering.membership.GroupScopedAuthorizer;
import ai.badmonkey.agentspaces.spring.AgentSpacesAutoConfiguration;
import ai.badmonkey.agentspaces.spring.Authorizers;
import ai.badmonkey.agentspaces.spring.DirectiveGates;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSpacesFactoryTest {

    @Test
    void theFactoryBuildsTheFabricFromProperties() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of())) {
            AgentSpaces spaces = ctx.getBean(AgentSpaces.class);
            assertThat(spaces.groupNames()).containsExactly("it-fleet");
            assertThat(spaces.group("it-fleet").spaceNames()).containsExactlyInAnyOrder("tasks", "findings", "data");
            assertThat(ctx.getBean(Authorizer.class))
                    .isInstanceOf(GroupScopedAuthorizer.class)
                    .isSameAs(ctx.getBean(Authorizers.class).forGroup("it-fleet"));
            assertThat(ctx.getBeansOfType(DirectiveGates.class)).hasSize(1);
            assertThat(ctx.getBean(FleetContext.class).groupName()).isEqualTo("it-fleet");
            assertThat(ctx.getBean(FleetLifecycle.class).isRunning()).isTrue();
        }
    }

    @Test
    void anApplicationEmbedderReplacesTheDefault() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("test.custom-embedder", "true"))) {
            assertThat(ctx.getBean(Embedder.class)).isInstanceOf(CustomEmbedder.class);
            assertThat(ctx.getBeansOfType(Embedder.class)).hasSize(1);
        }
    }

    @Test
    void agentspacesEnabledFalseKeepsEveryFabricBeanOut() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("agentspaces.enabled", "false"))) {
            assertThat(ctx.containsBean(AgentSpaces.class)).isFalse();
            assertThat(ctx.containsBean(FleetLifecycle.class)).isFalse();
        }
    }

    @Test
    void theChosenGroupMustBeJoined() {
        assertThatThrownBy(() -> {
            try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                    Map.of("agentspaces.micronaut.group", "other"))) {
                ctx.getBean(FleetContext.class);
            }
        }).hasMessageContaining("group 'other'").hasMessageContaining("it-fleet");
    }

    @Test
    void theReusedSpringWiringRunsWithNoSpringOnTheClasspath() {
        // The whole suite is the proof; this pins the assumption so a core
        // release that puts a Spring type in a reused signature fails here.
        assertThatThrownBy(() -> Class.forName("org.springframework.context.annotation.Bean"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThat(AgentSpacesAutoConfiguration.class.getAnnotations()).isEmpty();
    }
}
