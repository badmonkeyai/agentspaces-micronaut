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

import ai.badmonkey.agentspaces.agent.annotation.SpaceTake;
import ai.badmonkey.agentspaces.examples.micronaut.Order;
import ai.badmonkey.agentspaces.examples.micronaut.PriceRequest;
import ai.badmonkey.agentspaces.examples.micronaut.PricedOrder;
import ai.badmonkey.agentspaces.micronaut.SpaceAgent;
import io.micronaut.context.annotation.Requires;

/**
 * The pricing logic, once: {@link #price(PriceRequest)} takes
 * {@code PriceRequest} entries from the fleet's task space and writes the
 * {@code PricedOrder} into findings, and {@link OrdersController} calls the
 * same method for HTTP. The bean is a {@code @SpaceAgent}, so the service's
 * AgentCard lists {@code price} as an action any fleet agent can invoke
 * through {@code RemoteActions}. It is a plain bean: HTTP concerns such as
 * {@code @ExecuteOn} live on the controller, and an AOP interceptor binding
 * here would hide the fleet annotations from the binder.
 */
@SpaceAgent(name = "orders-service", description = "Prices customer orders",
        goals = {"price-orders"})
@Requires(env = "orders")
public class Pricer {

    private final OrdersRepository orders;

    public Pricer(OrdersRepository orders) {
        this.orders = orders;
    }

    @SpaceTake(space = "tasks", resultSpace = "findings", lease = "1m", pollTimeout = "PT0.2S",
            description = "Prices an order by id")
    public PricedOrder price(PriceRequest request) {
        Order order = orders.find(request.orderId())
                .orElseThrow(() -> new IllegalArgumentException("no order " + request.orderId()));
        return new PricedOrder(order.id(), order.qty() * order.unitPrice(), "orders-service");
    }
}
