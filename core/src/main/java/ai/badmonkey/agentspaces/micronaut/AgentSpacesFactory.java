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
package ai.badmonkey.agentspaces.micronaut;

import ai.badmonkey.agentspaces.agent.AgentIdentityFactory;
import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.api.spi.Authorizer;
import ai.badmonkey.agentspaces.api.spi.Embedder;
import ai.badmonkey.agentspaces.api.spi.SchemaRegistry;
import ai.badmonkey.agentspaces.identity.PeerIdentity;
import ai.badmonkey.agentspaces.peering.node.PeerNode;
import ai.badmonkey.agentspaces.runtime.FleetScheduler;
import ai.badmonkey.agentspaces.spring.AgentSpacesAutoConfiguration;
import ai.badmonkey.agentspaces.spring.AgentSpacesProperties;
import ai.badmonkey.agentspaces.spring.Authorizers;
import ai.badmonkey.agentspaces.spring.DirectiveGates;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.time.InstantSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The fabric as Micronaut beans. Every bean is built by the Spring starter's
 * own build methods on {@link AgentSpacesAutoConfiguration}, which carry no
 * Spring type in their signatures and run with no Spring on the classpath
 * (the Spring annotations on that class are unresolvable here, and the JVM
 * ignores them). That keeps the transport, TLS, group, space, key-ring, and
 * capability wiring in one place for both frameworks. Each bean yields to an
 * application-supplied one of the same type.
 */
@Factory
@Requires(property = "agentspaces.enabled", notEquals = "false")
public class AgentSpacesFactory {

    private final AgentSpacesAutoConfiguration wiring = new AgentSpacesAutoConfiguration();

    @Singleton
    @Requires(missingBeans = InstantSource.class)
    public InstantSource agentSpacesClock() {
        return wiring.agentSpacesClock();
    }

    @Singleton
    @Requires(missingBeans = PeerIdentity.class)
    public PeerIdentity agentSpacesIdentity(AgentSpacesProperties properties) {
        return wiring.agentSpacesIdentity(properties);
    }

    @Singleton
    @Requires(missingBeans = PeerNode.class)
    public PeerNode agentSpacesNode(AgentSpacesProperties properties, PeerIdentity identity) {
        return wiring.agentSpacesNode(properties, identity);
    }

    @Singleton
    @Requires(missingBeans = Authorizers.class)
    public Authorizers agentSpacesAuthorizers(AgentSpacesProperties properties,
                                              PeerIdentity identity, InstantSource clock) {
        return wiring.agentSpacesAuthorizers(properties, identity, clock);
    }

    @Singleton
    @Requires(missingBeans = Embedder.class)
    public Embedder agentSpacesEmbedder() {
        return wiring.agentSpacesEmbedder();
    }

    @Singleton
    @Requires(missingBeans = AgentIdentityFactory.class)
    public AgentIdentityFactory agentSpacesAgentIdentities(AgentSpacesProperties properties,
                                                           PeerIdentity identity,
                                                           InstantSource clock) {
        return wiring.agentSpacesAgentIdentities(properties, identity, clock);
    }

    @Singleton
    @Requires(missingBeans = AgentSpaces.class)
    public AgentSpaces agentSpaces(AgentSpacesProperties properties, PeerNode node,
                                   PeerIdentity identity, Authorizers authorizers,
                                   Embedder embedder, InstantSource clock,
                                   AgentIdentityFactory identities,
                                   @Nullable SchemaRegistry schemas) {
        // Issue #16: a SchemaRegistry singleton names the spaces the wiring builds
        // and every card the binder publishes, so an application may name its
        // entry types by an ontology namespace; absent, the default rule.
        return wiring.agentSpacesWithRegistry(properties, node, identity, authorizers, embedder,
                clock, identities, schemas);
    }

    @Singleton
    @Requires(missingBeans = Authorizer.class)
    public Authorizer agentSpacesAuthorizer(Authorizers authorizers, AgentSpaces spaces) {
        return wiring.agentSpacesAuthorizer(authorizers, spaces);
    }

    @Singleton
    @Requires(missingBeans = DirectiveGates.class)
    public DirectiveGates agentSpacesDirectiveGates(Authorizers authorizers, AgentSpaces spaces) {
        return wiring.agentSpacesDirectiveGates(authorizers, spaces);
    }

    @Singleton
    @Bean
    @Requires(missingBeans = FleetContext.class)
    public FleetContext fleetContext(AgentSpacesProperties properties, MicronautFleetSettings settings,
                                     AgentSpaces spaces, PeerIdentity identity,
                                     Authorizers authorizers) {
        String group = settings.getGroup().isBlank()
                ? firstGroup(properties) : settings.getGroup().trim();
        return new FleetContext(spaces, group, identity, authorizers.forGroup(group));
    }

    /**
     * The scheduler the runtime's participants (the tool catalogue, the model
     * server, the usage publisher) run on: Micronaut's scheduled executor.
     */
    @Singleton
    @Bean(preDestroy = "close")
    @Requires(missingBeans = FleetScheduler.class)
    public FleetScheduler fleetScheduler(@Named(TaskExecutors.SCHEDULED) ExecutorService scheduled) {
        return scheduled instanceof ScheduledExecutorService executor
                ? FleetScheduler.over(executor) : FleetScheduler.virtual("agentspaces-micronaut-");
    }

    static String firstGroup(AgentSpacesProperties properties) {
        if (properties.getGroups().isEmpty()) {
            throw new IllegalStateException("agentspaces.groups is empty: configure at least one"
                    + " group (name, founding or join, spaces)");
        }
        return properties.getGroups().get(0).getName();
    }
}
