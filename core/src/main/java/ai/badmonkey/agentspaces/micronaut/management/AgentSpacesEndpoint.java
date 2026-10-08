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
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@code /agentspaces}: the fleet summary plus every card the peer sees.
 * Sensitive by default, so Micronaut Security guards it when present; it
 * reads and never writes, since command-and-control is the console's job.
 */
@Endpoint(id = "agentspaces", defaultSensitive = true)
@Requires(classes = Endpoint.class)
@Requires(beans = FleetView.class)
public class AgentSpacesEndpoint {

    private final FleetView view;

    public AgentSpacesEndpoint(FleetView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    @Read
    public Map<String, Object> fleet() {
        Map<String, Object> document = new LinkedHashMap<>(view.summary());
        document.put("cards", view.cards());
        return document;
    }
}
