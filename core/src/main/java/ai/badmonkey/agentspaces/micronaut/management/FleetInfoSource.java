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
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.management.endpoint.info.InfoSource;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.Map;
import java.util.Objects;

/** {@code /info}: the peer id, profile, and groups under {@code agentspaces}. */
@Singleton
@Requires(classes = InfoSource.class)
@Requires(beans = FleetView.class)
public class FleetInfoSource implements InfoSource {

    private final FleetView view;

    public FleetInfoSource(FleetView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    @Override
    public Publisher<PropertySource> getSource() {
        Map<String, Object> summary = view.summary();
        return Publishers.just(PropertySource.of("agentspaces", Map.of("agentspaces", Map.of(
                "peer", summary.get("peer"),
                "profile", summary.get("profile"),
                "groups", summary.get("groups")))));
    }
}
