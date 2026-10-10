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
package ai.badmonkey.agentspaces.micronaut.tools;

import ai.badmonkey.agentspaces.micronaut.FleetContext;
import ai.badmonkey.agentspaces.micronaut.MicronautFleetSettings;
import ai.badmonkey.agentspaces.runtime.FleetScheduler;
import ai.badmonkey.agentspaces.tools.FleetToolCatalog;
import ai.badmonkey.agentspaces.tools.FleetToolFilter;
import ai.badmonkey.agentspaces.tools.FleetToolsChangedEvent;
import ai.badmonkey.agentspaces.tools.ToolNamingStrategy;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

/**
 * The live catalogue of fleet tools as a Micronaut bean: the runtime's
 * {@link FleetToolCatalog}, configured under {@code agentspaces.micronaut.tools.*},
 * started with the fleet, and forwarding every change to Micronaut's event
 * publisher as a {@link FleetToolsChangedEvent}, which the MCP export hears.
 */
@Singleton
@Order(120)
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.micronaut.tools.enabled", notEquals = "false")
public class FleetActions extends FleetToolCatalog {

    public FleetActions(FleetContext fleet, MicronautFleetSettings settings,
                        FleetToolFilter filter, ToolNamingStrategy naming,
                        ApplicationEventPublisher<FleetToolsChangedEvent> events,
                        FleetScheduler scheduler) {
        super(fleet, settings.getTools().toSettings(), filter, naming, scheduler);
        onChange(events::publishEvent);
    }
}
