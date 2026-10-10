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
package ai.badmonkey.agentspaces.micronaut.enroll;

import ai.badmonkey.agentspaces.agent.annotation.OnEstimate;
import ai.badmonkey.agentspaces.agent.annotation.Part;
import ai.badmonkey.agentspaces.agent.annotation.Propose;
import ai.badmonkey.agentspaces.agent.annotation.SpaceJoin;
import ai.badmonkey.agentspaces.agent.annotation.SpaceReduce;
import ai.badmonkey.agentspaces.agent.annotation.SpaceTake;
import ai.badmonkey.agentspaces.agent.join.Joined;
import ai.badmonkey.agentspaces.capabilities.aggregate.PushSumAggregate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The space-name inference behind the enroller's failure message covers the 0.3.0 annotations. */
class FleetEnrollerSpacesTest {

    public record Cue(String id) {
    }

    public record Acc(String id, int n) {
    }

    @SuppressWarnings("unused")
    public static class Everything {
        @SpaceTake(space = "take-in", resultSpace = "take-out")
        public Cue take(Cue cue) {
            return cue;
        }

        @SpaceJoin(space = "join-in", key = "id", resultSpace = "join-out",
                parts = {@Part(Cue.class), @Part(value = Acc.class, space = "join-part")})
        public Cue join(Joined joined) {
            return null;
        }

        @Propose(space = "cues", vote = "votes", options = {"yes", "no"}, quorum = 1)
        public String propose(Cue cue) {
            return cue.id();
        }

        @OnEstimate(prefix = "load", resultSpace = "readings")
        public Cue estimate(PushSumAggregate.Estimate estimate) {
            return null;
        }

        @SpaceReduce(space = "ledger", key = "id")
        public Acc reduce(Acc acc, Cue cue) {
            return acc;
        }
    }

    @SuppressWarnings("unused")
    public static class Inferred {
        @SpaceJoin(key = "id", parts = {@Part(Cue.class)})
        public void join(Joined joined) {
        }

        @Propose(options = {"yes", "no"}, quorum = 1)
        public String propose(Cue cue) {
            return cue.id();
        }

        @OnEstimate(prefix = "load")
        public void estimate(PushSumAggregate.Estimate estimate) {
        }

        @SpaceReduce(key = "id")
        public Acc reduce(Acc acc, Cue cue) {
            return acc;
        }
    }

    @Test
    void everyNamedSpaceOfThe030AnnotationsIsReferenced() {
        assertThat(FleetEnroller.referencedSpaces(Everything.class)).containsExactlyInAnyOrder(
                "take-in", "take-out",
                "join-in", "join-out", "join-part",
                "cues", "votes",
                "readings",
                "ledger");
    }

    @Test
    void inferredNamesAreLeftOut() {
        assertThat(FleetEnroller.referencedSpaces(Inferred.class)).isEmpty();
        assertThat(ai.badmonkey.agentspaces.agent.AgentBinder.hasBindings(Inferred.class)).isTrue();
        assertThat(ai.badmonkey.agentspaces.agent.AgentBinder.hasBindings(Everything.class)).isTrue();
    }
}
