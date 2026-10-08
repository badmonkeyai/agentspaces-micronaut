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
package ai.badmonkey.agentspaces.examples.micronaut.analyst;

import ai.badmonkey.agentspaces.examples.micronaut.Order;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

/**
 * Resolved through the fleet: the orders service's AssetCard carries its URI,
 * and {@code agentspaces.micronaut.registry.enabled} turns those cards into
 * instances for the service id {@code orders}. No URL is configured anywhere.
 */
@Client("orders")
@Requires(env = "analyst")
public interface OrdersClient {

    @Get("/orders/{id}")
    Order get(String id);
}
