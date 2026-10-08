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

import ai.badmonkey.agentspaces.examples.micronaut.Order;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The orders, in memory; a real service would back this with Micronaut Data. */
@Singleton
@Requires(env = "orders")
public class OrdersRepository {

    private final Map<String, Order> orders = new LinkedHashMap<>();

    public OrdersRepository() {
        orders.put("1", new Order("1", "widget", 3, 9.50));
        orders.put("2", new Order("2", "gadget", 1, 120.00));
        orders.put("3", new Order("3", "gizmo", 12, 2.25));
    }

    public Optional<Order> find(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    public List<Order> all() {
        return List.copyOf(orders.values());
    }
}
