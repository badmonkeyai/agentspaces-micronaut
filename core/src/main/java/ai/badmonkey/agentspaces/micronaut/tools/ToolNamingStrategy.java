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

/**
 * The naming strategy as this library first named it; the type now lives in
 * {@code agentspaces-runtime}. A bean of this type still replaces the default.
 *
 * @deprecated implement {@link ai.badmonkey.agentspaces.tools.ToolNamingStrategy}; this alias goes in the next release
 */
@Deprecated(since = "0.2.0", forRemoval = true)
@FunctionalInterface
public interface ToolNamingStrategy extends ai.badmonkey.agentspaces.tools.ToolNamingStrategy {

    static ai.badmonkey.agentspaces.tools.ToolNamingStrategy prefixed(String prefix) {
        return ai.badmonkey.agentspaces.tools.ToolNamingStrategy.prefixed(prefix);
    }

    static String sanitize(String name) {
        return ai.badmonkey.agentspaces.tools.ToolNamingStrategy.sanitize(name);
    }
}
