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
package ai.badmonkey.agentspaces.examples.micronaut.ai;

import ai.badmonkey.agentspaces.agent.annotation.SpaceTake;
import ai.badmonkey.agentspaces.micronaut.SpaceAgent;
import dev.langchain4j.model.chat.ChatModel;
import io.micronaut.context.annotation.Requires;

/**
 * The worker peer's two agents. Each completes in its take space, which is
 * where a remote caller awaits the result, and each becomes a tool
 * ({@code fleet_summarizer_Brief}, {@code fleet_pricer_PriceRequest}) on
 * every other peer. The summarizer uses the peer's own LangChain4j model;
 * the take lease is short because the bridge renews it on every round-trip.
 */
public final class Workers {

    private Workers() {
    }

    /** Summarizes with the model the worker peer holds the key for. */
    @SpaceAgent(name = "summarizer", description = "Summarizes a topic into a short briefing", goals = {"summarize"})
    @Requires(env = "worker")
    public static class Summarizer {
        private final ChatModel model;

        public Summarizer(ChatModel model) {
            this.model = model;
        }

        @SpaceTake(space = "tasks", lease = "2m", pollTimeout = "PT0.2S", description = "Summarizes a topic")
        public Summary summarize(Brief brief) {
            return new Summary(brief.topic(), model.chat("Summarize '" + brief.topic() + "' in "
                    + brief.sentences() + " sentences."));
        }
    }

    /** Prices without a model: not every agent needs one. */
    @SpaceAgent(name = "pricer", description = "Prices an item for a number of people", goals = {"price"})
    @Requires(env = "worker")
    public static class Pricer {

        @SpaceTake(space = "tasks", pollTimeout = "PT0.2S", description = "Prices an item per head")
        public Quote price(PriceRequest request) {
            return new Quote(request.item(), request.people(), request.people() * 42.0);
        }
    }
}
