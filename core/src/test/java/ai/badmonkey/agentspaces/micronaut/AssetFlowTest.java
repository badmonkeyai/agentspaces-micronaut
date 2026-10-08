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
import ai.badmonkey.agentspaces.micronaut.assets.AssetConnectors;
import ai.badmonkey.agentspaces.micronaut.support.Contexts;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssetFlowTest {

    @Test
    @Timeout(120)
    void anAssetProviderBeanIsServedAndQueriedAcrossTheFleet() {
        int seedPort = Contexts.freePort();
        try (ApplicationContext orders = Contexts.run(Contexts.fleet(seedPort, 0), Map.of("test.app", "orders"));
             ApplicationContext analyst = Contexts.run(Contexts.fleet(Contexts.freePort(), seedPort), Map.of())) {
            assertThat(orders.getBean(AssetConnectors.class).runtimes()).containsOnlyKeys("assets-connector");

            FleetContext fleet = analyst.getBean(FleetContext.class);
            assertThat(Contexts.await(Duration.ofSeconds(30), () -> !fleet.group().discovery()
                    .find(AssetCard.class, card -> "orders".equals(card.asset())).isEmpty())).isTrue();
            AssetCard card = fleet.group().discovery().find(AssetCard.class, c -> true).get(0);
            assertThat(card.uri()).isEqualTo("postgres://ops/public.orders");
            assertThat(card.issuer()).isEqualTo(orders.getBean(FleetContext.class).peerId());

            DataQueryClient client = new DataQueryClient(fleet.group().space("data"), "analyst",
                    analyst.getBean(Authorizer.class));
            assertThat(client.fetch("orders", Map.of(), Duration.ofSeconds(30)))
                    .hasValueSatisfying(fetched -> assertThat(fetched.result().rows())
                            .hasSize(2).anySatisfy(row -> assertThat(row).containsEntry("sku", "widget")));
        }
    }

    @Test
    void withoutProvidersNothingIsServedAndTheDataSpaceIsNotRequired() {
        Map<String, Object> props = Contexts.fleet(Contexts.freePort(), 0);
        props.remove("agentspaces.groups[0].spaces[2].name");
        props.remove("agentspaces.groups[0].spaces[2].settle-window-millis");
        try (ApplicationContext ctx = Contexts.run(props, Map.of())) {
            assertThat(ctx.getBean(AssetConnectors.class).runtimes()).isEmpty();
        }
    }
}
