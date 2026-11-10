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

import ai.badmonkey.agentspaces.agent.annotation.PdtRef;
import ai.badmonkey.agentspaces.agent.annotation.SpaceTake;
import ai.badmonkey.agentspaces.micronaut.SpaceAgent;
import ai.badmonkey.agentspaces.pdt.refs.Ref;
import ai.badmonkey.agentspaces.pdt.values.PMap;
import io.micronaut.context.annotation.Requires;

/**
 * A fleet bean with a {@code @PdtRef} field and a verb whose trailing parameter is one (PDT 2 phase 7, P2-X-03):
 * the enroller binds it after the factory's facade is adopted, so both resolve through the group's {@code Pdt}.
 */
@SpaceAgent(name = "it-noter", description = "Notes tasks in a ref", goals = {"note"})
@Requires(property = "test.app", value = "noter")
public class Noter {

    @PdtRef("facts")
    public Ref<PMap<String, String>> facts;

    @SpaceTake(space = "tasks", lease = "PT10M", pollTimeout = "PT0.2S")
    public void note(ItTask task, @PdtRef("notes") Ref<PMap<String, String>> notes) {
        notes.swap(m -> m.assoc(task.topic(), "noted"));
    }
}
