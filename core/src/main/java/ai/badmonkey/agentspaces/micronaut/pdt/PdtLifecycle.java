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

import ai.badmonkey.agentspaces.micronaut.FleetParticipant;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

import java.util.Objects;

/**
 * Starts the facades with the fabric (srad/agentspaces-pdt.md PD-C-03): once
 * the node ticks, every {@link ai.badmonkey.agentspaces.pdt.Pdt} starts and
 * joins its group's tick, the way the content swarm is driven; on shutdown
 * they close before the node does. Ordered before the console, so a panel
 * that reads the facades finds them running.
 */
@Singleton
@Order(90)
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.pdt.enabled", value = "true")
public class PdtLifecycle implements FleetParticipant {

    private final Pdts pdts;

    public PdtLifecycle(Pdts pdts) {
        this.pdts = Objects.requireNonNull(pdts, "pdts");
    }

    @Override
    public void start() {
        pdts.start();
    }

    @Override
    public void stop() {
        pdts.close();
    }
}
