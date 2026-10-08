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

import ai.badmonkey.agentspaces.micronaut.a2a.A2aLifecycle;
import ai.badmonkey.agentspaces.micronaut.console.ConsoleLifecycle;
import ai.badmonkey.agentspaces.micronaut.support.Contexts;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsoleAndA2aTest {

    @Test
    @Timeout(60)
    void theConsoleServesWhenEnabledWithApplicationPanels() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of(
                "test.app", "console",
                "agentspaces.console.enabled", "true",
                "agentspaces.console.port", "0"))) {
            int port = ctx.getBean(ConsoleLifecycle.class).boundPort();
            assertThat(port).isPositive();
            assertThat(Contexts.get("http://127.0.0.1:" + port + "/api/v1")).contains("overview");
            assertThat(Contexts.get("http://127.0.0.1:" + port + "/api/v1/panels")).contains("it-panel");
        }
    }

    @Test
    void theConsoleAndTheGatewayAreOffByDefault() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of())) {
            assertThat(ctx.containsBean(ConsoleLifecycle.class)).isFalse();
            assertThat(ctx.containsBean(A2aLifecycle.class)).isFalse();
        }
    }

    @Test
    @Timeout(60)
    void theA2aGatewayServesItsCardOnLoopback() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of(
                "test.app", "researcher",
                "agentspaces.a2a.enabled", "true",
                "agentspaces.a2a.space", "tasks",
                "agentspaces.a2a.port", "0"))) {
            int port = ctx.getBean(A2aLifecycle.class).boundPort();
            assertThat(port).isPositive();
            assertThat(Contexts.get("http://127.0.0.1:" + port + "/.well-known/agent-card.json"))
                    .contains("it-fleet");
        }
    }

    @Test
    void aRoutableA2aBindWithoutAGateFailsAtStartup() {
        assertThatThrownBy(() -> Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of(
                "agentspaces.a2a.enabled", "true",
                "agentspaces.a2a.space", "tasks",
                "agentspaces.a2a.bind", "0.0.0.0",
                "agentspaces.a2a.port", "0")).close())
                .hasMessageContaining("routable");
    }
}
