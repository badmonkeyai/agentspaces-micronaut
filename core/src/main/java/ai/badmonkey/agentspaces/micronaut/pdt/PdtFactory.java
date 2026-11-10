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
package ai.badmonkey.agentspaces.micronaut.pdt;

import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.micronaut.FleetContext;
import ai.badmonkey.agentspaces.pdt.Pdt;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.InstantSource;
import java.util.List;

/**
 * Persistent data trees as Micronaut beans (srad/agentspaces-pdt.md PD-C-03):
 * from the {@code agentspaces.pdt.*} keys of appendix C, the Spring starter's
 * keys (PD-C-02), a {@link Pdt} per group collected in {@link Pdts}, and the
 * fleet group's facade as the {@link Pdt} bean. Each bean yields to an
 * application-supplied one of the same type; a {@code NodeStore} bean replaces
 * the configured store ({@link PdtStores}).
 *
 * <p>This wiring builds the facades against {@code agentspaces-pdt} directly,
 * after the shared Spring wiring has built the groups: {@link PdtStores} hands
 * that wiring each group's tree store and the {@code agentspaces.pdt.spaces.*}
 * blocks, and {@link Pdts} adopts every facade into the wiring's holder, so the
 * spaces built over its deferred seam and the {@code @PdtRef} sites reach the
 * facade (PDT 2 phase 7, P2-X-03, P2-X-04).
 */
@Factory
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.pdt.enabled", value = "true")
public class PdtFactory {

    /**
     * The facades, one per group with a content swarm.
     *
     * @param spaces   the fabric
     * @param stores   the node store per group
     * @param settings the keys
     * @param refs     the per-ref modes, or an empty list
     * @param clock    the clock
     * @param properties the shared keys, whose {@code agentspaces.store.challenge-deadline} the facades take
     * @param holder   the shared wiring's holder, which adopts every facade (PDT 2 phase 7, P2-X-03, P2-X-04)
     * @return the registry
     */
    @Singleton
    @Bean(preDestroy = "close")
    @Requires(missingBeans = Pdts.class)
    public Pdts pdts(AgentSpaces spaces, PdtStores stores, PdtSettings settings, List<RefSettings> refs,
                     InstantSource clock, ai.badmonkey.agentspaces.spring.AgentSpacesProperties properties,
                     ai.badmonkey.agentspaces.spring.Pdts holder) {
        return new Pdts(spaces, stores, settings, refs, clock, properties.getStore().getChallengeDeadline(), holder);
    }

    /** The fleet group's facade ({@code agentspaces.micronaut.group}, else the first group). */
    @Singleton
    @Requires(missingBeans = Pdt.class)
    public Pdt pdt(Pdts pdts, FleetContext fleet) {
        return pdts.pdt(fleet.groupName());
    }
}
