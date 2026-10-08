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

import ai.badmonkey.agentspaces.agent.annotation.AgentSpec;
import ai.badmonkey.agentspaces.agent.annotation.SpaceTake;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/** A plain singleton with the core annotation and no stereotype: must enroll too. */
@Singleton
@AgentSpec(name = "plain-spec", description = "A plain @AgentSpec singleton")
@Requires(property = "test.app", value = "plain-spec")
public class PlainSpecAgent {

    @SpaceTake(space = "tasks", pollTimeout = "PT0.2S", resultSpace = "findings")
    public ItFinding work(ItTask task) {
        return new ItFinding(task.topic(), "plain: " + task.topic());
    }
}
