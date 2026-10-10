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

import ai.badmonkey.agentspaces.agent.Entries;
import ai.badmonkey.agentspaces.agent.Tagged;
import ai.badmonkey.agentspaces.agent.annotation.SpaceNotify;
import ai.badmonkey.agentspaces.agent.annotation.SpaceRef;
import ai.badmonkey.agentspaces.api.space.Lease;
import ai.badmonkey.agentspaces.api.space.Space;
import ai.badmonkey.agentspaces.micronaut.SpaceAgent;
import io.micronaut.context.annotation.Requires;

import java.time.Duration;

/**
 * A {@code @SpaceNotify} that forks: it writes a marker through its
 * {@code @SpaceRef} handle mid-method and returns an {@code Entries} fork
 * holding a {@code Tagged} finding and a tally, both written to the watched
 * space while the cue stays where it was.
 */
@SpaceAgent(name = "forker", description = "Forks every task into a tagged finding and a tally")
@Requires(property = "test.app", value = "forker")
public class Forker {

    @SpaceRef("data")
    Space data;

    @SpaceNotify(space = "tasks", produces = {ItFinding.class, ItTally.class})
    public Entries fork(ItTask task) {
        data.write(new ItFinding("ref:" + task.topic(), "via @SpaceRef"), Lease.of(Duration.ofMinutes(10)));
        return Entries.of(
                Tagged.of(new ItFinding(task.topic(), "forked"), "kind", "fork"),
                new ItTally(task.topic(), task.priority()));
    }
}
