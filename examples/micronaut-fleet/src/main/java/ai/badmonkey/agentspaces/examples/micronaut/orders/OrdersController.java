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
import ai.badmonkey.agentspaces.examples.micronaut.PriceRequest;
import ai.badmonkey.agentspaces.examples.micronaut.PricedOrder;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

/**
 * The HTTP front door: orders by id, and {@code POST /orders/price}, which
 * calls the same {@link Pricer#price(PriceRequest)} the fleet's task space
 * drives. Blocking work runs off the event loop ({@code @ExecuteOn}); the
 * agent lives in {@link Pricer}, a plain bean with no HTTP concerns.
 */
@Controller("/orders")
@Requires(env = "orders")
@ExecuteOn(TaskExecutors.BLOCKING)
public class OrdersController {

    private final OrdersRepository orders;
    private final Pricer pricer;

    public OrdersController(OrdersRepository orders, Pricer pricer) {
        this.orders = orders;
        this.pricer = pricer;
    }

    @Get("/{id}")
    public HttpResponse<Order> get(String id) {
        return orders.find(id).map(HttpResponse::ok).orElseGet(HttpResponse::notFound);
    }

    @Post("/price")
    public PricedOrder price(@Body PriceRequest request) {
        return pricer.price(request);
    }
}
