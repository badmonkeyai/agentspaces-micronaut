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

import ai.badmonkey.agentspaces.agent.annotation.Part;
import ai.badmonkey.agentspaces.agent.annotation.SpaceJoin;
import ai.badmonkey.agentspaces.agent.join.Joined;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/** A plain singleton whose only fleet annotation is {@code @SpaceJoin}: enrolls through the processor. */
@Singleton
@Requires(property = "test.app", value = "join-only")
public class JoinOnlyAgent {

    @SpaceJoin(space = "tasks", key = "topic", parts = {@Part(value = ItTask.class, atLeast = 2)},
            pollTimeout = "PT0.2S", resultSpace = "findings")
    public ItFinding pick(Joined joined) {
        return new ItFinding(joined.key(), "joined: " + joined.all(ItTask.class).size());
    }
}
