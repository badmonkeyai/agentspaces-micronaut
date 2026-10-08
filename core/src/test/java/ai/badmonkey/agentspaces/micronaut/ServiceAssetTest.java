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

import ai.badmonkey.agentspaces.api.ad.AssetCard;
import ai.badmonkey.agentspaces.api.spi.Authorizer;
import ai.badmonkey.agentspaces.connect.DataQueryClient;
import ai.badmonkey.agentspaces.micronaut.assets.ServiceAssetCard;
import ai.badmonkey.agentspaces.micronaut.support.Contexts;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceAssetTest {

    private static Map<String, Object> service(int port, int seedPort, String query) {
        Map<String, Object> props = new HashMap<>(Contexts.fleet(port, seedPort));
        props.put("micronaut.server.port", "-1");
        props.put("micronaut.application.name", "orders");
        props.put("test.server", "true");
        props.put("agentspaces.micronaut.service-asset.enabled", "true");
        props.put("agentspaces.micronaut.service-asset.query", query);
        props.put("agentspaces.micronaut.service-asset.allowed-paths[0]", "/hello");
        return props;
    }

    @Test
    @Timeout(120)
    void theEmbeddedServerPublishesAnAssetCardAndAnswersAllowedGets() {
        int seedPort = Contexts.freePort();
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, service(seedPort, 0, "get"));
        try (ApplicationContext orders = server.getApplicationContext();
             ApplicationContext analyst = Contexts.run(Contexts.fleet(Contexts.freePort(), seedPort), Map.of())) {
            ServiceAssetCard serviceAsset = orders.getBean(ServiceAssetCard.class);
            assertThat(Contexts.await(Duration.ofSeconds(10), () -> serviceAsset.card().isPresent())).isTrue();
            AssetCard card = serviceAsset.card().orElseThrow();
            assertThat(card.asset()).isEqualTo("orders");
            assertThat(card.uri()).isEqualTo(server.getURI().toString());
            assertThat(card.shape()).isEqualTo("http");
            assertThat(card.freshness()).isEqualTo("live");
            assertThat(card.access()).containsEntry("mode", "http").containsEntry("query", "aspace:cap/data-query");

            FleetContext fleet = analyst.getBean(FleetContext.class);
            assertThat(Contexts.await(Duration.ofSeconds(30), () -> !fleet.group().discovery()
                    .find(AssetCard.class, c -> "orders".equals(c.asset())).isEmpty())).isTrue();

            DataQueryClient client = new DataQueryClient(fleet.group().space("data"), "analyst",
                    analyst.getBean(Authorizer.class));
            assertThat(client.fetch("orders", Map.of("path", "/hello"), Duration.ofSeconds(30)))
                    .hasValueSatisfying(fetched -> {
                        assertThat(fetched.result().error()).isNull();
                        assertThat(fetched.result().rows()).singleElement()
                                .satisfies(row -> {
                                    assertThat(row).containsEntry("status", "200");
                                    assertThat(row.get("body")).isEqualTo("hello from orders");
                                });
                    });
            assertThat(client.fetch("orders", Map.of("path", "/health"), Duration.ofSeconds(30)))
                    .hasValueSatisfying(fetched -> assertThat(fetched.result().error())
                            .contains("not allowed"));
        }
    }

    @Test
    @Timeout(60)
    void queryNoneRefusesEveryQuery() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class,
                service(Contexts.freePort(), 0, "none"));
        try (ApplicationContext orders = server.getApplicationContext()) {
            ServiceAssetCard serviceAsset = orders.getBean(ServiceAssetCard.class);
            assertThat(Contexts.await(Duration.ofSeconds(10), () -> serviceAsset.card().isPresent())).isTrue();
            assertThat(serviceAsset.card().orElseThrow().access()).containsEntry("query", "none");
        }
    }

    @Test
    void theServiceAssetNeedsAName() {
        Map<String, Object> props = service(Contexts.freePort(), 0, "none");
        props.remove("micronaut.application.name");
        assertThatThrownBy(() -> Contexts.run(props, Map.of()).getBean(ServiceAssetCard.class))
                .hasMessageContaining("micronaut.application.name");
    }
}
