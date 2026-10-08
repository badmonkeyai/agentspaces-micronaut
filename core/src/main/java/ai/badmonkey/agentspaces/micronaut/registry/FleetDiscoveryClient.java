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
package ai.badmonkey.agentspaces.micronaut.registry;

import ai.badmonkey.agentspaces.api.ad.AssetCard;
import ai.badmonkey.agentspaces.api.spi.Authorizer;
import ai.badmonkey.agentspaces.micronaut.FleetContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The fleet as a service registry: a {@link DiscoveryClient} over the
 * group's ad-cache, answering a service id with every peer whose AssetCard
 * advertises an {@code http} asset of that name (the cards
 * {@code ServiceAssetCard} publishes). Micronaut's composite discovery client
 * includes it, so {@code @Client("orders")} load-balances across the peers
 * that serve {@code orders}, and a lapsed card drops its instance. Only cards
 * from issuers the profile's authorizer permits {@code CONNECTOR_SERVE} count.
 */
@Singleton
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.micronaut.registry.enabled", value = "true")
@Requires(classes = DiscoveryClient.class)
public class FleetDiscoveryClient implements DiscoveryClient {

    private final FleetContext fleet;

    public FleetDiscoveryClient(FleetContext fleet) {
        this.fleet = Objects.requireNonNull(fleet, "fleet");
    }

    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        return Publishers.just(instances(serviceId));
    }

    @Override
    public Publisher<List<String>> getServiceIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (AssetCard card : httpCards(null)) {
            ids.add(card.asset());
        }
        return Publishers.just(new ArrayList<>(ids));
    }

    /** @return the instances advertising {@code serviceId} right now */
    public List<ServiceInstance> instances(String serviceId) {
        List<ServiceInstance> instances = new ArrayList<>();
        for (AssetCard card : httpCards(serviceId)) {
            Map<String, String> access = card.access();
            ServiceInstance.Builder builder = ServiceInstance.builder(card.asset(), URI.create(card.uri()))
                    .metadata(access);
            if (access.containsKey("instance-id")) {
                builder.instanceId(access.get("instance-id"));
            } else {
                builder.instanceId(card.issuer().value());
            }
            instances.add(builder.build());
        }
        return instances;
    }

    private List<AssetCard> httpCards(String serviceId) {
        Authorizer authorizer = fleet.authorizer();
        return fleet.group().discovery().find(AssetCard.class, card ->
                "http".equals(card.access().get("mode"))
                        && (serviceId == null || serviceId.equals(card.asset()))
                        && authorizer.permits(card.issuer(), Authorizer.Operation.CONNECTOR_SERVE,
                        card.asset()));
    }

    @Override
    public String getDescription() {
        return "agentspaces";
    }

    @Override
    public void close() {
    }
}
