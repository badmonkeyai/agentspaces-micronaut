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
package ai.badmonkey.agentspaces.micronaut.langchain4j.support;

import ai.badmonkey.agentspaces.connect.AssetProvider;
import ai.badmonkey.agentspaces.connect.TableAssetProvider;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** A data asset on the worker peer, for the discovery tools. */
@Factory
@Requires(property = "test.app", value = "worker")
public class OrdersAssets {

    @Singleton
    public AssetProvider ordersTable() {
        return new TableAssetProvider("orders", "mem://orders", "Customer orders by sku and quantity", "table",
                Duration.ofMinutes(5), List.of(Map.of("sku", "widget", "qty", "3")));
    }
}
