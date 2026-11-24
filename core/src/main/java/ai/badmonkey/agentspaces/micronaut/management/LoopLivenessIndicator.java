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

import ai.badmonkey.agentspaces.peering.node.NodeLoop;
import ai.badmonkey.agentspaces.peering.node.PeerNode;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.management.health.indicator.annotation.Liveness;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@code /health/liveness}: the node loop's health as its watchdog judges it, the Micronaut
 * counterpart of the Spring starter's {@code LoopLiveness} (srad/agentspaces-event-loop.md EL-H-08,
 * EL-H-14). DOWN once the watchdog escalated, so a supervisor that probes liveness restarts the
 * process; UP for OK and DEGRADED, with the state and reasons in the details. A host with no probe
 * sets {@code agentspaces.watchdog.escalation: exit} instead.
 */
@Singleton
@Liveness
@Requires(classes = HealthIndicator.class)
@Requires(beans = FleetView.class)
public class LoopLivenessIndicator implements HealthIndicator {

    private final PeerNode node;

    public LoopLivenessIndicator(PeerNode node) {
        this.node = Objects.requireNonNull(node, "node");
    }

    @Override
    public Publisher<HealthResult> getResult() {
        return Publishers.just(result(node.loopHealth()));
    }

    /** The result for one health reading; package-private for tests. */
    static HealthResult result(NodeLoop.Health health) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("state", health.state().name());
        details.put("reasons", health.reasons());
        HealthStatus status = health.state() == NodeLoop.HealthState.DOWN ? HealthStatus.DOWN : HealthStatus.UP;
        return HealthResult.builder("agentspaces-loop", status).details(details).build();
    }
}
