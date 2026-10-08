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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The worker peer's model in the test: the summarizer's direct calls get a
 * briefing; the orchestrator's calls (which arrive through the fleet with
 * tool specifications) walk the plan: summarize, then price, then answer.
 */
@Singleton
@Primary
@Named("default")
@Requires(property = "test.scripted", value = "true")
public class ScriptedModel implements ChatModel {

    public final AtomicInteger calls = new AtomicInteger();
    private final List<ChatModelListener> listeners;

    /** Like a provider builder, the model takes every listener bean (take lease, usage). */
    public ScriptedModel(List<ChatModelListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        calls.incrementAndGet();
        AiMessage answer;
        if (request.toolSpecifications() == null || request.toolSpecifications().isEmpty()) {
            UserMessage user = (UserMessage) request.messages().get(request.messages().size() - 1);
            answer = AiMessage.from("briefing: " + user.singleText());
        } else {
            List<ToolExecutionResultMessage> results = request.messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance).map(ToolExecutionResultMessage.class::cast).toList();
            answer = switch (results.size()) {
                case 0 -> AiMessage.from(ToolExecutionRequest.builder().id("c1").name("fleet_summarizer_Brief")
                        .arguments("{\"topic\":\"team retreats\",\"sentences\":2}").build());
                case 1 -> AiMessage.from(ToolExecutionRequest.builder().id("c2").name("fleet_pricer_PriceRequest")
                        .arguments("{\"item\":\"lunch\",\"people\":12}").build());
                default -> AiMessage.from("Plan: " + results.stream().map(ChatMessage::toString)
                        .map(ScriptedModel::digest).reduce((a, b) -> a + " | " + b).orElse(""));
            };
        }
        return ChatResponse.builder().aiMessage(answer).modelName("gpt-4.1-mini").tokenUsage(new TokenUsage(20, 10, 30))
                .finishReason(answer.hasToolExecutionRequests() ? FinishReason.TOOL_EXECUTION : FinishReason.STOP).build();
    }

    private static String digest(String text) {
        int at = text.indexOf("text = ");
        return at < 0 ? text : text.substring(at + 7).replaceAll("[\\\\\"}]", " ").strip();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return ChatRequestParameters.builder().modelName("gpt-4.1-mini").build();
    }
}
