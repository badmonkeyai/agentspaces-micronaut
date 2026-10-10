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

import ai.badmonkey.agentspaces.micronaut.MicronautFleetSettings;
import ai.badmonkey.agentspaces.tools.FleetToolFilter;
import ai.badmonkey.agentspaces.tools.ToolNamingStrategy;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/** The replaceable defaults of the tool catalogue (the runtime's types; a bean of either alias also counts). */
@Factory
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.micronaut.tools.enabled", notEquals = "false")
public class FleetToolsFactory {

    @Singleton
    @Requires(missingBeans = FleetToolFilter.class)
    public FleetToolFilter fleetToolFilter(MicronautFleetSettings settings) {
        MicronautFleetSettings.Tools tools = settings.getTools();
        return FleetToolFilter.of(tools.getIncludeAgents(), tools.getExcludeAgents(), tools.isRequireAttested());
    }

    @Singleton
    @Requires(missingBeans = ToolNamingStrategy.class)
    public ToolNamingStrategy toolNamingStrategy(MicronautFleetSettings settings) {
        return ToolNamingStrategy.prefixed(settings.getTools().getPrefix());
    }
}
