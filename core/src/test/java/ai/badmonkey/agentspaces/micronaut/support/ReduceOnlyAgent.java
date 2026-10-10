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
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * A plain singleton whose only fleet annotation is {@code @SpaceReduce}: it
 * enrolls through the compile-time path like any other method annotation.
 */
@Singleton
@Requires(property = "test.app", value = "reduce-only")
public class ReduceOnlyAgent {

    @SpaceReduce(space = "tasks", key = "topic", pollTimeout = "PT0.2S")
    public ItTally apply(ItTally tally, ItTask task) {
        return new ItTally(task.topic(), (tally == null ? 0 : tally.points()) + task.priority());
    }
}
