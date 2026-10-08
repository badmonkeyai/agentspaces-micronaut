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
package ai.badmonkey.agentspaces.examples.micronaut.orders;

import ai.badmonkey.agentspaces.connect.AssetProvider;
import ai.badmonkey.agentspaces.connect.TableAssetProvider;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The orders table as an AssetCard: every {@link AssetProvider} bean is served
 * through the connector SDK, so an analyst anywhere on the fleet discovers
 * {@code orders-table} and reads its rows through the data space.
 */
@Factory
@Requires(env = "orders")
public class OrdersAssets {

    @Singleton
    public AssetProvider ordersTable(OrdersRepository repository) {
        List<Map<String, String>> rows = repository.all().stream()
                .map(o -> Map.of("id", o.id(), "sku", o.sku(), "qty", String.valueOf(o.qty()),
                        "unit-price", String.valueOf(o.unitPrice())))
                .toList();
        // "orders" is the service's own card (its HTTP presence); the table is "orders-table".
        return new TableAssetProvider("orders-table", "mem://orders-service/orders",
                "Customer orders held by the orders service", "table", Duration.ofMinutes(5), rows);
    }
}
