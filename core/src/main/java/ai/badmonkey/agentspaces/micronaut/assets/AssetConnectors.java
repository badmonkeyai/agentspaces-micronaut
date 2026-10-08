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

import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.api.space.Space;
import ai.badmonkey.agentspaces.connect.AssetProvider;
import ai.badmonkey.agentspaces.connect.ConnectorRuntime;
import ai.badmonkey.agentspaces.micronaut.FleetContext;
import ai.badmonkey.agentspaces.micronaut.FleetParticipant;
import ai.badmonkey.agentspaces.micronaut.MicronautFleetSettings;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Serves this peer's assets through the connector SDK: every application
 * {@link AssetProvider} bean, plus any provider registered later (the
 * service card), behind one {@link ConnectorRuntime} on the data space
 * ({@code agentspaces.micronaut.assets.space}), publishing AssetCards and
 * answering {@code aspace:cap/data-query} requests under the profile's
 * authorizer ({@code CONNECTOR_SERVE}). Started after annotated beans enroll,
 * refreshed on the card cadence, stopped before the node closes. With no
 * providers the data space is not required.
 */
@Singleton
@Order(100)
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.micronaut.assets.enabled", notEquals = "false")
public class AssetConnectors implements FleetParticipant {

    private static final Logger LOG = LoggerFactory.getLogger(AssetConnectors.class);

    private final FleetContext fleet;
    private final MicronautFleetSettings.Assets settings;
    private final String connectorName;
    private final CompositeAssetProvider composite = new CompositeAssetProvider();
    private ConnectorRuntime runtime;
    private boolean started;

    public AssetConnectors(FleetContext fleet, MicronautFleetSettings settings,
                           Environment environment, List<AssetProvider> providers) {
        this.fleet = Objects.requireNonNull(fleet, "fleet");
        this.settings = settings.getAssets();
        this.connectorName = environment.getProperty("micronaut.application.name", String.class)
                .filter(name -> !name.isBlank()).orElse("assets") + "-connector";
        providers.forEach(composite::add);
    }

    @Override
    public synchronized void start() {
        started = true;
        if (!composite.providers().isEmpty()) {
            ensureRunning();
        }
    }

    /**
     * Adds a provider; its cards publish on the next refresh, or now if the
     * runtime was idle for lack of providers.
     *
     * @param provider the provider
     */
    public synchronized void register(AssetProvider provider) {
        composite.add(provider);
        if (!started) {
            return;
        }
        if (runtime == null) {
            ensureRunning();
        } else {
            runtime.refreshCards();
        }
    }

    private void ensureRunning() {
        if (runtime != null) {
            return;
        }
        Space data = fleet.requiredSpace(settings.getSpace(), "agentspaces.micronaut.assets.space");
        AgentSpaces.GroupContext group = fleet.group();
        runtime = new ConnectorRuntime(data, group.discovery(), fleet.identity(), connectorName,
                composite, group.id(), group.clock(), settings.getFreshness())
                .authorizer(fleet.authorizer());
        runtime.start();
        LOG.info("serving {} asset provider(s) as '{}' on space '{}'",
                composite.providers().size(), connectorName, settings.getSpace());
    }

    @Override
    public synchronized void refresh() {
        if (runtime != null) {
            runtime.refreshCards();
        }
    }

    @Override
    public synchronized void stop() {
        started = false;
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
    }

    /** @return the running connector by name, for the management views */
    public synchronized Map<String, ConnectorRuntime> runtimes() {
        return runtime == null ? Map.of() : Map.of(connectorName, runtime);
    }

    /** @return the providers served, application beans and registered ones */
    public List<AssetProvider> providers() {
        return composite.providers();
    }

    /** @return the runtime, once started */
    public synchronized Optional<ConnectorRuntime> runtime() {
        return Optional.ofNullable(runtime);
    }
}
