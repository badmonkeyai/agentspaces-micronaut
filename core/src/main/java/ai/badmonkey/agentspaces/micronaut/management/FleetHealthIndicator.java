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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.management.health.indicator.annotation.Readiness;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.Objects;

/**
 * {@code /health} readiness: UP while the lifecycle runs; the details are the
 * fleet summary (peer, groups with member counts, cards, connectors).
 */
@Singleton
@Readiness
@Requires(classes = HealthIndicator.class)
@Requires(beans = FleetView.class)
public class FleetHealthIndicator implements HealthIndicator {

    private final FleetView view;

    public FleetHealthIndicator(FleetView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    @Override
    public Publisher<HealthResult> getResult() {
        // Membership is informational: a founder running alone is a healthy peer.
        boolean up = view.running();
        return Publishers.just(HealthResult.builder("agentspaces", up ? HealthStatus.UP : HealthStatus.DOWN)
                .details(view.summary()).build());
    }
}
