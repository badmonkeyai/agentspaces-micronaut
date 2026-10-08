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
package ai.badmonkey.agentspaces.micronaut.support;

import ai.badmonkey.agentspaces.connect.AssetProvider;
import ai.badmonkey.agentspaces.connect.TableAssetProvider;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** The orders table of example 10, as an application AssetProvider bean. */
@Factory
@Requires(property = "test.app", value = "orders")
public class OrdersAssets {

    @Singleton
    public AssetProvider ordersTable() {
        return new TableAssetProvider("orders", "postgres://ops/public.orders",
                "Customer orders", "table", Duration.ofMinutes(5), List.of(
                Map.of("id", "1", "sku", "widget", "qty", "3"),
                Map.of("id", "2", "sku", "gadget", "qty", "1")));
    }
}
