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

import ai.badmonkey.agentspaces.agent.annotation.SpaceReduce;
import ai.badmonkey.agentspaces.micronaut.SpaceAgent;
import io.micronaut.context.annotation.Requires;

/**
 * Folds tasks per the {@code topic} tag (the field is ignored) under a short
 * accumulator lease, so an idle key lapses and starts again from null.
 */
@SpaceAgent(name = "tag-folder", description = "Sums priority points per topic tag")
@Requires(property = "test.app", value = "tag-folder")
public class TagFolder {

    @SpaceReduce(space = "tasks", keyTag = "topic", name = "tagfold", lease = "1s",
            pollTimeout = "PT0.2S", accumulatorLease = "2s")
    public ItTally apply(ItTally tally, ItTask task) {
        return new ItTally(task.topic(), (tally == null ? 0 : tally.points()) + task.priority());
    }
}
