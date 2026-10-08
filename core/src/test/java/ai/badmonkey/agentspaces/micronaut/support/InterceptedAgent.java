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

import ai.badmonkey.agentspaces.agent.annotation.SpaceTake;
import ai.badmonkey.agentspaces.micronaut.SpaceAgent;
import io.micronaut.context.annotation.Requires;
import io.micronaut.retry.annotation.Retryable;

/** An AOP-intercepted agent: the binder cannot see its annotations, so startup must say so. */
@SpaceAgent(description = "Intercepted by @Retryable")
@Retryable
@Requires(property = "test.app", value = "intercepted")
public class InterceptedAgent {

    @SpaceTake(space = "tasks", pollTimeout = "PT0.2S", resultSpace = "findings")
    public ItFinding work(ItTask task) {
        return new ItFinding(task.topic(), "intercepted");
    }
}
