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

import io.micronaut.runtime.Micronaut;

/**
 * The orders microservice: a Micronaut HTTP service that is also a fleet peer.
 * One pricing method serves HTTP and the shared task space, its orders table
 * is an AssetCard any fleet agent can query, and its
 * HTTP presence is an AssetCard the analyst's {@code @Client("orders")}
 * resolves through the fleet. Run with the {@code orders} environment.
 */
public final class OrdersService {

    private OrdersService() {
    }

    public static void main(String[] args) {
        Micronaut.build(args).environments("orders").start();
    }
}
