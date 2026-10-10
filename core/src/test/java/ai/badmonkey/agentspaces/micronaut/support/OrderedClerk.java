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

import ai.badmonkey.agentspaces.agent.annotation.OrderedTake;
import ai.badmonkey.agentspaces.micronaut.SpaceAgent;
import io.micronaut.context.annotation.Requires;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Confirms each task exactly once through the ordered log, and remembers which. */
@SpaceAgent(name = "ordered-clerk", description = "Confirms tasks through the ordered log")
@Requires(property = "test.app", value = "ordered-clerk")
public class OrderedClerk {

    public final List<String> confirmed = new CopyOnWriteArrayList<>();

    public OrderedClerk(OrderedWiring wiring) {
        // The coordinator is registered by the wiring bean's constructor.
    }

    @OrderedTake(space = "tasks", lease = "30s", pollTimeout = "200ms", resultSpace = "findings")
    public ItFinding confirm(ItTask task) {
        confirmed.add(task.topic());
        return new ItFinding(task.topic(), "ordered: " + task.topic());
    }
}
