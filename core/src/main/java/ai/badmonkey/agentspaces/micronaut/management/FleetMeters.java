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
package ai.badmonkey.agentspaces.micronaut.management;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Objects;

/**
 * Micrometer gauges per group: members, local and remote agent cards, asset
 * cards; and one for enrolled agents. Registered when a {@code MeterRegistry}
 * bean exists.
 */
@Singleton
@Context
@Requires(classes = MeterRegistry.class)
@Requires(beans = {MeterRegistry.class, FleetView.class})
public class FleetMeters {

    public FleetMeters(MeterRegistry registry, FleetView view) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(view, "view");
        Gauge.builder("agentspaces.agents.enrolled", view, v -> v.enrolledAgents()).register(registry);
        for (String group : view.groupNames()) {
            Gauge.builder("agentspaces.members", view, v -> v.members(group))
                    .tag("group", group).register(registry);
            Gauge.builder("agentspaces.cards.agents.local", view, v -> v.localAgentCards(group))
                    .tag("group", group).register(registry);
            Gauge.builder("agentspaces.cards.agents.remote", view, v -> v.remoteAgentCards(group))
                    .tag("group", group).register(registry);
            Gauge.builder("agentspaces.cards.assets", view, v -> v.assetCards(group))
                    .tag("group", group).register(registry);
        }
    }
}
