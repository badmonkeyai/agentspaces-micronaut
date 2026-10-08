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
package ai.badmonkey.agentspaces.micronaut.langchain4j.support;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/** Scripted model beans for the contexts that need one. */
@Factory
public class ScriptedModels {

    /** The orchestrator's model: calls the summarizer tool once, then answers with its result. */
    @Singleton
    @Primary
    @Named("scripted")
    @Requires(property = "test.app", value = "orchestrator")
    public ScriptedChatModel orchestratorModel() {
        return new ScriptedChatModel("scripted")
                .then(request -> {
                    boolean hasFleetTool = request.toolSpecifications() != null && request.toolSpecifications().stream()
                            .anyMatch(spec -> spec.name().equals("fleet_summarizer_Brief"));
                    if (!hasFleetTool) {
                        return AiMessage.from("no fleet tools offered");
                    }
                    return AiMessage.from(ToolExecutionRequest.builder().id("call-1").name("fleet_summarizer_Brief")
                            .arguments("{\"topic\":\"offsite\",\"sentences\":2}").build());
                })
                .then(request -> {
                    String last = request.messages().stream()
                            .filter(ToolExecutionResultMessage.class::isInstance)
                            .map(m -> ((ToolExecutionResultMessage) m).text())
                            .reduce((a, b) -> b).orElse("(no tool result)");
                    return AiMessage.from("plan based on: " + last);
                });
    }

    /** The server peer's model, served to the fleet under the name "scripted". */
    @Singleton
    @Named("scripted")
    @Requires(property = "test.app", value = "server")
    public ScriptedChatModel serverModel() {
        return new ScriptedChatModel("scripted").thenText("served by the fleet");
    }

    /** The server's streaming twin, under the same bean name. */
    @Singleton
    @Named("scripted")
    @Requires(property = "test.app", value = "server")
    public StreamingChatModel serverStreamingModel(@Named("scripted") ScriptedChatModel model) {
        return new ScriptedStreamingChatModel(model);
    }
}
