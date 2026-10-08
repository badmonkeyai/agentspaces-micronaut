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

import ai.badmonkey.agentspaces.api.ad.AssetCard;
import ai.badmonkey.agentspaces.api.space.Lease;
import ai.badmonkey.agentspaces.connect.DataQueryClient;
import ai.badmonkey.agentspaces.examples.micronaut.PriceRequest;
import ai.badmonkey.agentspaces.micronaut.FleetContext;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * What the analyst does once the orders service is on the fleet: ask for a
 * price through the space, read the orders table through the data space, and
 * fetch one order over HTTP through the registry. The test drives it; the
 * {@code main} runs it once the service's card appears.
 */
@Singleton
@Requires(env = "analyst")
public class AnalystRun {

    private static final Logger LOG = LoggerFactory.getLogger(AnalystRun.class);

    private final FleetContext fleet;
    private final OrdersClient orders;

    public AnalystRun(FleetContext fleet, OrdersClient orders) {
        this.fleet = fleet;
        this.orders = orders;
    }

    /** @return whether the orders service's cards have reached this peer */
    public boolean ordersServiceVisible() {
        return !fleet.group().discovery()
                .find(AssetCard.class, card -> "orders".equals(card.asset())).isEmpty();
    }

    /** Writes a price request the orders service's controller will take. */
    public void requestPrice(String orderId) {
        fleet.group().space("tasks").write(new PriceRequest(orderId), Lease.of(Duration.ofMinutes(5)));
    }

    /** Reads the orders table through the data space. */
    public Optional<DataQueryClient.Fetched> fetchOrders() {
        DataQueryClient client = new DataQueryClient(fleet.group().space("data"), "analyst",
                fleet.authorizer());
        return client.fetch("orders-table", Map.of(), Duration.ofSeconds(30));
    }

    /** Fetches one order over HTTP, the service resolved through the fleet. */
    public String describe(String orderId) {
        var order = orders.get(orderId);
        String line = "order " + order.id() + ": " + order.qty() + " x " + order.sku();
        LOG.info(line);
        return line;
    }
}
