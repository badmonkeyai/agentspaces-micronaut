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
package ai.badmonkey.agentspaces.micronaut.assets;

import ai.badmonkey.agentspaces.api.ad.AssetCard;
import ai.badmonkey.agentspaces.common.id.GroupId;
import ai.badmonkey.agentspaces.common.id.PeerId;
import ai.badmonkey.agentspaces.connect.AssetProvider;
import ai.badmonkey.agentspaces.micronaut.FleetParticipant;
import ai.badmonkey.agentspaces.micronaut.MicronautFleetSettings;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Order;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The microservice itself as an AssetCard: once the embedded server has
 * bound, one card names the service ({@code micronaut.application.name}),
 * its URI, shape {@code http}, freshness {@code live}, and under
 * {@code access} the scheme, instance id, and whether it answers queries.
 * Fleet agents discover the service like any other asset, and the registry
 * ({@code agentspaces.micronaut.registry.enabled}) turns these cards into
 * {@code @Client} targets on other peers. The card is served by the peer's
 * single connector runtime ({@link AssetConnectors}) beside the application's
 * own providers, so {@code agentspaces.micronaut.assets.enabled} must stay on.
 *
 * <p>With {@code service-asset.query=get}, a data query whose {@code path}
 * parameter starts with one of {@code allowed-paths} performs a GET against
 * the local server and returns one row ({@code status}, {@code content-type},
 * {@code body}), so a fleet agent with no network path to the service can
 * read from it through the space. Off by default; every query goes through
 * the connector runtime's authorizer check like any other.
 */
@Singleton
@Order(110)
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.micronaut.service-asset.enabled", value = "true")
@Requires(beans = AssetConnectors.class)
public class ServiceAssetCard implements FleetParticipant {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceAssetCard.class);

    private final AssetConnectors connectors;
    private final MicronautFleetSettings.ServiceAsset settings;
    private final String applicationName;
    private final String instanceId;
    private final List<ServiceAssetCustomizer> customizers;
    private boolean registered;
    private volatile AssetCard published;

    public ServiceAssetCard(AssetConnectors connectors, MicronautFleetSettings settings,
                            Environment environment, List<ServiceAssetCustomizer> customizers) {
        this.connectors = Objects.requireNonNull(connectors, "connectors");
        this.settings = settings.getServiceAsset();
        this.applicationName = environment.getProperty("micronaut.application.name", String.class)
                .orElse("");
        this.instanceId = environment.getProperty("micronaut.application.instance.id", String.class)
                .orElse("");
        this.customizers = List.copyOf(customizers);
        if (assetName().isEmpty()) {
            throw new IllegalStateException("agentspaces.micronaut.service-asset.enabled=true needs"
                    + " an asset name: set micronaut.application.name or"
                    + " agentspaces.micronaut.service-asset.asset");
        }
        if (!"none".equals(this.settings.getQuery()) && !"get".equals(this.settings.getQuery())) {
            throw new IllegalStateException("agentspaces.micronaut.service-asset.query must be"
                    + " 'none' or 'get', not '" + this.settings.getQuery() + "'");
        }
    }

    @Override
    public void start() {
        // The card needs the bound port; it registers on ServerStartupEvent.
    }

    @EventListener
    public synchronized void onServerStarted(ServerStartupEvent event) {
        if (registered) {
            return;
        }
        EmbeddedServer server = event.getSource();
        URI uri = settings.getExternalUri().isBlank() ? server.getURI()
                : URI.create(settings.getExternalUri().trim());
        connectors.register(new Provider(uri, server.getURI()));
        registered = true;
        LOG.info("published service asset '{}' at {}", assetName(), uri);
    }

    @Override
    public void stop() {
        // The connectors close the runtime that serves the card.
    }

    /** @return the card as last published, if the server has started */
    public Optional<AssetCard> card() {
        return Optional.ofNullable(published);
    }

    String assetName() {
        return settings.getAsset().isBlank() ? applicationName.trim() : settings.getAsset().trim();
    }

    /** The provider behind the card: one asset, and GET queries when allowed. */
    final class Provider implements AssetProvider {

        private final URI advertised;
        private final URI local;
        private final HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(settings.getQueryTimeout())
                .build();

        Provider(URI advertised, URI local) {
            this.advertised = advertised;
            this.local = local;
        }

        @Override
        public List<AssetCard> describeAssets(GroupId group, PeerId issuer, Instant now) {
            Map<String, String> access = new LinkedHashMap<>();
            access.put("mode", "http");
            access.put("scheme", advertised.getScheme());
            access.put("query", "get".equals(settings.getQuery()) ? "aspace:cap/data-query" : "none");
            if (!instanceId.isBlank()) {
                access.put("instance-id", instanceId);
            }
            String description = settings.getDescription().isBlank()
                    ? "Micronaut service " + assetName() : settings.getDescription();
            AssetCard card = new AssetCard("aspace://" + group.value() + "/asset/" + assetName(),
                    issuer, group, now, Duration.ofMinutes(15), assetName(), advertised.toString(),
                    description, "http", "live", Map.of(), access);
            for (ServiceAssetCustomizer customizer : customizers) {
                card = Objects.requireNonNull(customizer.customize(card), "customized card");
            }
            published = card;
            return List.of(card);
        }

        @Override
        public QueryResult query(String asset, Map<String, String> parameters) {
            if (!"get".equals(settings.getQuery())) {
                return QueryResult.failed("asset '" + asset + "' is not queryable");
            }
            String path = parameters.getOrDefault("path", "");
            if (!allowed(path)) {
                return QueryResult.failed("path not allowed: '" + path + "'");
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(local.resolve(path))
                        .timeout(settings.getQueryTimeout()).GET().build();
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                byte[] body = response.body();
                if (body.length > settings.getMaxBodyBytes()) {
                    body = Arrays.copyOf(body, settings.getMaxBodyBytes());
                }
                Map<String, String> row = new LinkedHashMap<>();
                row.put("status", String.valueOf(response.statusCode()));
                row.put("content-type", response.headers().firstValue("content-type").orElse(""));
                row.put("body", new String(body, StandardCharsets.UTF_8));
                return QueryResult.of(List.of(row));
            } catch (IOException e) {
                return QueryResult.failed("GET " + path + " failed: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return QueryResult.failed("GET " + path + " interrupted");
            }
        }

        private boolean allowed(String path) {
            if (path.isEmpty() || !path.startsWith("/") || path.contains("..")) {
                return false;
            }
            for (String prefix : settings.getAllowedPaths()) {
                if (!prefix.isBlank() && path.startsWith(prefix.trim())) {
                    return true;
                }
            }
            return false;
        }
    }
}
