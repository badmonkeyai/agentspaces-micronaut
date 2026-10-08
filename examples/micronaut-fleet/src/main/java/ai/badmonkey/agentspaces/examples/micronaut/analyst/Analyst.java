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

import io.micronaut.runtime.Micronaut;

/**
 * The analyst: a peer with no HTTP server of its own that drives the orders
 * service three ways: through the task space (a {@code PriceRequest} entry
 * the service's controller takes), through the data space (the orders
 * AssetCard, read with {@code DataQueryClient}), and over HTTP through the
 * fleet registry ({@code @Client("orders")}). Run with the {@code analyst}
 * environment.
 */
public final class Analyst {

    private Analyst() {
    }

    public static void main(String[] args) {
        Micronaut.build(args).environments("analyst").start();
    }
}
