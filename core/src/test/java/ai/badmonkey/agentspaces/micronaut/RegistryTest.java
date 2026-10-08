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

import ai.badmonkey.agentspaces.micronaut.registry.FleetDiscoveryClient;
import ai.badmonkey.agentspaces.micronaut.support.Contexts;
import ai.badmonkey.agentspaces.micronaut.support.OrdersClient;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryTest {

    @Test
    @Timeout(120)
    void aClientResolvesAServiceThroughTheFleetsAssetCards() {
        int seedPort = Contexts.freePort();
        Map<String, Object> orders = new HashMap<>(Contexts.fleet(seedPort, 0));
        orders.put("micronaut.server.port", "-1");
        orders.put("micronaut.application.name", "orders");
        orders.put("test.server", "true");
        orders.put("agentspaces.micronaut.service-asset.enabled", "true");
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, orders);
        try (ApplicationContext ordersCtx = server.getApplicationContext();
             ApplicationContext analyst = Contexts.run(Contexts.fleet(Contexts.freePort(), seedPort),
                     Map.of("test.app", "analyst", "agentspaces.micronaut.registry.enabled", "true"))) {
            FleetDiscoveryClient registry = analyst.getBean(FleetDiscoveryClient.class);
            assertThat(Contexts.await(Duration.ofSeconds(30), () -> !registry.instances("orders").isEmpty()))
                    .isTrue();
            assertThat(registry.instances("orders")).singleElement()
                    .satisfies(instance -> assertThat(instance.getURI()).isEqualTo(server.getURI()));

            assertThat(analyst.getBean(OrdersClient.class).hello()).isEqualTo("hello from orders");
        }
    }

    @Test
    void theRegistryIsOffByDefault() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of())) {
            assertThat(ctx.containsBean(FleetDiscoveryClient.class)).isFalse();
        }
    }
}
