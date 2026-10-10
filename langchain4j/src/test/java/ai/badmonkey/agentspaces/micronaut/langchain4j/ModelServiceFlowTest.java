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
package ai.badmonkey.agentspaces.micronaut.langchain4j;

import ai.badmonkey.agentspaces.langchain4j.model.FleetChatModel;
import ai.badmonkey.agentspaces.langchain4j.model.FleetStreamingChatModel;
import ai.badmonkey.agentspaces.langchain4j.model.ModelServer;
import ai.badmonkey.agentspaces.micronaut.langchain4j.support.Contexts;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.data.message.UserMessage;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ModelServiceFlowTest {

    private static Map<String, Object> withModelSpace(Map<String, Object> props) {
        Map<String, Object> all = new HashMap<>(props);
        all.put("agentspaces.groups[0].spaces[3].name", "model-requests");
        all.put("agentspaces.groups[0].spaces[3].settle-window-millis", "100");
        return all;
    }

    @Test
    @Timeout(180)
    void aKeylessPeerCallsAndStreamsThroughAServerPeer() throws Exception {
        int seedPort = Contexts.freePort();
        try (ApplicationContext server = Contexts.run(withModelSpace(Contexts.fleet(seedPort, 0)), Map.of(
                     "test.app", "server",
                     "agentspaces.micronaut.langchain4j.model-server.enabled", "true"));
             ApplicationContext client = Contexts.run(withModelSpace(Contexts.fleet(Contexts.freePort(), seedPort)), Map.of(
                     "agentspaces.micronaut.langchain4j.model-client.enabled", "true",
                     "agentspaces.micronaut.langchain4j.model-client.model", "scripted"))) {
            assertThat(server.getBean(ModelServer.class).isRunning()).isTrue();
            // The keyless peer's only ChatModel is the fleet.
            ChatModel model = client.getBean(ChatModel.class);
            assertThat(model).isInstanceOf(FleetChatModel.class);
            assertThat(client.getBean(StreamingChatModel.class)).isInstanceOf(FleetStreamingChatModel.class);

            ChatResponse response = model.chat(ChatRequest.builder().messages(UserMessage.from("hello")).build());
            assertThat(response.aiMessage().text()).isEqualTo("served by the fleet");
            assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(15);
            assertThat(response.modelName()).isEqualTo("scripted");
            assertThat(server.getBean(ModelServer.class).served()).isEqualTo(1);

            List<String> partials = new CopyOnWriteArrayList<>();
            CompletableFuture<ChatResponse> complete = new CompletableFuture<>();
            client.getBean(StreamingChatModel.class).chat(
                    ChatRequest.builder().messages(UserMessage.from("stream it")).build(),
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String partialResponse) {
                            partials.add(partialResponse);
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse completeResponse) {
                            complete.complete(completeResponse);
                        }

                        @Override
                        public void onError(Throwable error) {
                            complete.completeExceptionally(error);
                        }
                    });
            ChatResponse streamed = complete.get(60, TimeUnit.SECONDS);
            assertThat(String.join("", partials)).isEqualTo("served by the fleet");
            assertThat(partials.size()).isGreaterThan(1);
            assertThat(streamed.aiMessage().text()).isEqualTo("served by the fleet");
            assertThat(streamed.tokenUsage().totalTokenCount()).isEqualTo(15);
        }
    }

    @Test
    void theModelServiceIsOffByDefault() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of())) {
            assertThat(ctx.containsBean(FleetChatModel.class)).isFalse();
            assertThat(ctx.containsBean(ModelServer.class)).isFalse();
        }
    }
}
