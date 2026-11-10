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

import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.identity.PeerIdentity;
import ai.badmonkey.agentspaces.micronaut.FleetContext;
import ai.badmonkey.agentspaces.micronaut.pdt.Pdts;
import ai.badmonkey.agentspaces.spring.AgentSpacesProperties;
import ai.badmonkey.agentspaces.spring.Authorizers;
import ai.badmonkey.agentspaces.store.ContentStore;
import ai.badmonkey.agentspaces.store.StorePolicy;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.time.InstantSource;

/**
 * Content stores as Micronaut beans (PDT 2 phase 3, P2-S-10): {@code agentspaces.store.enabled=true} makes this
 * peer a store over every group's facade, from the keys of appendix C, which the Spring starter reads too. A store
 * needs {@code agentspaces.content.enabled} and {@code agentspaces.pdt.enabled}, and the factory refuses to build
 * one otherwise, naming the keys. A {@link StorePolicy} bean replaces the policy the keys build, and the fleet
 * group's store is the {@link ContentStore} bean.
 */
@Factory
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.store.enabled", value = "true")
public class StoreFactory {

    /**
     * The stores, one per group with a facade.
     *
     * @throws IllegalStateException when {@code agentspaces.pdt.enabled} is not true
     */
    @Singleton
    @Bean(preDestroy = "close")
    @Requires(missingBeans = ContentStores.class)
    public ContentStores contentStores(AgentSpaces spaces, @Nullable Pdts pdts, AgentSpacesProperties properties,
                                       PeerIdentity identity, Authorizers authorizers, @Nullable StorePolicy policy,
                                       InstantSource clock) {
        if (pdts == null) {
            throw new IllegalStateException("agentspaces.store.enabled=true needs agentspaces.pdt.enabled=true:"
                    + " a store pins and holds trees through the group's Pdt facade");
        }
        return new ContentStores(spaces, pdts, properties, identity, authorizers, policy, clock);
    }

    /** The fleet group's store ({@code agentspaces.micronaut.group}, else the first group). */
    @Singleton
    @Requires(missingBeans = ContentStore.class)
    public ContentStore contentStore(ContentStores stores, FleetContext fleet) {
        return stores.store(fleet.groupName());
    }
}
