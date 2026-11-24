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
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The loop liveness indicator maps the watchdog's DOWN, and only DOWN, to a failed liveness (EL-H-08). */
class LoopLivenessIndicatorTest {

    @Test
    void downIsNotLiveAndDegradedStillIs() {
        HealthResult down = LoopLivenessIndicator.result(
                new NodeLoop.Health(NodeLoop.HealthState.DOWN, List.of("loop stalled 30000 ms")));
        assertThat(down.getStatus()).isEqualTo(HealthStatus.DOWN);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) down.getDetails();
        assertThat(details).containsEntry("state", "DOWN").containsEntry("reasons", List.of("loop stalled 30000 ms"));

        HealthResult degraded = LoopLivenessIndicator.result(
                new NodeLoop.Health(NodeLoop.HealthState.DEGRADED, List.of("overrun")));
        assertThat(degraded.getStatus()).isEqualTo(HealthStatus.UP);
        assertThat(LoopLivenessIndicator.result(new NodeLoop.Health(NodeLoop.HealthState.OK, List.of())).getStatus())
                .isEqualTo(HealthStatus.UP);
    }
}
