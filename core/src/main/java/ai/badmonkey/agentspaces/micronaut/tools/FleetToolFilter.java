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

import java.util.List;

/**
 * The filter as this library first named it; the type now lives in
 * {@code agentspaces-runtime}. A bean of this type still replaces the default.
 *
 * @deprecated implement {@link ai.badmonkey.agentspaces.tools.FleetToolFilter}; this alias goes in the next release
 */
@Deprecated(since = "0.2.0", forRemoval = true)
@FunctionalInterface
public interface FleetToolFilter extends ai.badmonkey.agentspaces.tools.FleetToolFilter {

    static ai.badmonkey.agentspaces.tools.FleetToolFilter of(List<String> include, List<String> exclude,
                                                             boolean requireAttested) {
        return ai.badmonkey.agentspaces.tools.FleetToolFilter.of(include, exclude, requireAttested);
    }
}
