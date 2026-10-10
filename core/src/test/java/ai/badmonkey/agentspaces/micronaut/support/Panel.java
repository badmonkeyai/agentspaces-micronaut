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

import ai.badmonkey.agentspaces.agent.annotation.Ballot;
import ai.badmonkey.agentspaces.agent.annotation.CapabilityRef;
import ai.badmonkey.agentspaces.agent.annotation.OnDecision;
import ai.badmonkey.agentspaces.agent.annotation.Propose;
import ai.badmonkey.agentspaces.agent.capability.VoteClient;
import ai.badmonkey.agentspaces.capabilities.vote.VoteCapability;
import ai.badmonkey.agentspaces.micronaut.SpaceAgent;
import io.micronaut.context.annotation.Requires;

/**
 * One bean that proposes, votes, and records: a task opens a vote, the
 * ballot approves it, and the decision lands as a finding that carries the
 * tally read through the injected {@code @CapabilityRef} client.
 */
@SpaceAgent(name = "panel", description = "Opens, votes on, and records a vote per task")
@Requires(property = "test.app", value = "panel")
public class Panel {

    @CapabilityRef
    VoteClient votes;

    @Propose(space = "tasks", vote = "votes", prefix = "task:", key = "topic",
            options = {"approve", "deny"}, quorum = 1)
    public String open(ItTask task) {
        return "Approve " + task.topic() + "?";
    }

    @Ballot(space = "votes", prefix = "task:")
    public String judge(VoteCapability.Proposal proposal) {
        return "approve";
    }

    @OnDecision(space = "votes", prefix = "task:", resultSpace = "findings")
    public ItFinding record(VoteCapability.Decision decision) {
        return new ItFinding(decision.proposalId(),
                "decided: " + decision.winner() + " tally " + votes.tally(decision.proposalId()));
    }
}
