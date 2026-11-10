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
package ai.badmonkey.agentspaces.micronaut.store;

import ai.badmonkey.agentspaces.micronaut.FleetParticipant;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

import java.util.Objects;

/**
 * Starts the content stores after the facades (PDT 2 phase 3, P2-S-10): ordered after {@code PdtLifecycle}, so
 * every facade runs before its store publishes a card; on shutdown the stores close first and withdraw their
 * cards.
 */
@Singleton
@Order(91)
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.store.enabled", value = "true")
public class StoreLifecycle implements FleetParticipant {

    private final ContentStores stores;

    public StoreLifecycle(ContentStores stores) {
        this.stores = Objects.requireNonNull(stores, "stores");
    }

    @Override
    public void start() {
        stores.start();
    }

    @Override
    public void stop() {
        stores.close();
    }
}
