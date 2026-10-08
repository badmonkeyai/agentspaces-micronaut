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
package ai.badmonkey.agentspaces.micronaut.support;

import ai.badmonkey.agentspaces.console.ConsolePanel;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
@Requires(property = "test.app", value = "console")
public class ItPanel implements ConsolePanel {

    @Override
    public String id() {
        return "it-panel";
    }

    @Override
    public String title() {
        return "Integration";
    }

    @Override
    public Object data() {
        return Map.of("suite", "micronaut");
    }
}
