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

import java.util.concurrent.CopyOnWriteArrayList;

/** The researcher of the Spring integration test, as a Micronaut stereotype bean. */
@SpaceAgent(name = "it-researcher", description = "Researches integration topics",
        goals = {"research"})
@Requires(property = "test.app", value = "researcher")
public class Researcher {

    public final CopyOnWriteArrayList<String> worked = new CopyOnWriteArrayList<>();

    @SpaceTake(space = "tasks", lease = "PT10M", pollTimeout = "PT0.2S", resultSpace = "findings")
    public ItFinding research(ItTask task) {
        worked.add(task.topic());
        return new ItFinding(task.topic(), "done: " + task.topic());
    }
}
