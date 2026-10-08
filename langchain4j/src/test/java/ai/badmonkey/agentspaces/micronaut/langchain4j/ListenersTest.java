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

import ai.badmonkey.agentspaces.agent.TakeContext;
import ai.badmonkey.agentspaces.api.space.Lease;
import ai.badmonkey.agentspaces.api.space.Space;
import ai.badmonkey.agentspaces.api.space.TakenEntry;
import ai.badmonkey.agentspaces.api.space.Template;
import ai.badmonkey.agentspaces.common.id.AgentId;
import ai.badmonkey.agentspaces.micronaut.FleetContext;
import ai.badmonkey.agentspaces.micronaut.langchain4j.embed.LangChain4jEmbedder;
import ai.badmonkey.agentspaces.micronaut.langchain4j.support.Brief;
import ai.badmonkey.agentspaces.micronaut.langchain4j.support.Contexts;
import ai.badmonkey.agentspaces.micronaut.langchain4j.usage.FleetUsageListener;
import ai.badmonkey.agentspaces.micronaut.langchain4j.worker.TakeLeaseListener;
import ai.badmonkey.agentspaces.springai.model.wire.ModelUsage;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.micronaut.context.ApplicationContext;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ListenersTest {

    @Test
    void theTakeLeaseListenerKeepsAShortTakeAliveAcrossRoundTrips() throws Exception {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of())) {
            Space tasks = ctx.getBean(FleetContext.class).group().space("tasks");
            tasks.write(new Brief("lease", 1), Lease.of(Duration.ofMinutes(5)));
            Duration lease = Duration.ofSeconds(1);
            TakenEntry<Brief> taken = tasks.take(Template.of(Brief.class), Lease.of(lease), Duration.ofSeconds(5)).orElseThrow();
            TakeContext take = new TakeContext(taken, lease, new AgentId(ctx.getBean(FleetContext.class).peerId(), "w"), "tasks");
            TaskScheduler scheduler = ctx.getBean(TaskScheduler.class, Qualifiers.byName(TaskExecutors.SCHEDULED));
            TakeLeaseListener listener = new TakeLeaseListener(scheduler, Duration.ofMillis(300), () -> Optional.of(take));

            Map<Object, Object> attributes = new HashMap<>();
            ChatRequest request = ChatRequest.builder().messages(UserMessage.from("x")).build();
            listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OTHER, attributes));
            Thread.sleep(2500); // well past the one-second lease: the ticker renewed it
            listener.onResponse(new ChatModelResponseContext(
                    ChatResponse.builder().aiMessage(AiMessage.from("y")).build(), request, ModelProvider.OTHER, attributes));
            // Still taken: nobody else can take it.
            assertThat(tasks.take(Template.of(Brief.class), Lease.of(lease), Duration.ofMillis(300))).isEmpty();
            tasks.complete(taken);
        }
    }

    @Test
    void theUsageListenerRecordsTokensPerResponse() {
        List<ModelUsage> recorded = new ArrayList<>();
        FleetUsageListener listener = new FleetUsageListener(recorded::add, "peer-1");
        ChatRequest request = ChatRequest.builder().messages(UserMessage.from("x")).build();
        listener.onResponse(new ChatModelResponseContext(ChatResponse.builder().aiMessage(AiMessage.from("y"))
                .modelName("m").tokenUsage(new TokenUsage(3, 4, 7)).build(), request, ModelProvider.OPEN_AI, new HashMap<>()));
        assertThat(recorded).singleElement().satisfies(usage -> {
            assertThat(usage.model()).isEqualTo("m");
            assertThat(usage.totalTokens()).isEqualTo(7);
            assertThat(usage.peer()).isEqualTo("peer-1");
            assertThat(usage.provider()).isEqualTo("open_ai");
        });
    }

    @Test
    void theEmbedderNormalizesCachesAndReportsItsIdentity() {
        EmbeddingModel model = new EmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                return Response.from(segments.stream().map(s -> Embedding.from(new float[] {3f, 4f})).toList());
            }

            @Override
            public int dimension() {
                return 2;
            }
        };
        LangChain4jEmbedder embedder = new LangChain4jEmbedder(model, "tiny", 16);
        double[] vector = embedder.embed("hello");
        assertThat(vector).containsExactly(new double[] {0.6, 0.8}, org.assertj.core.data.Offset.offset(1e-9));
        embedder.embed("hello");
        assertThat(embedder.cached()).isEqualTo(1);
        assertThat(embedder.dimensions()).isEqualTo(2);
        assertThat(embedder.identity()).isEqualTo("langchain4j:tiny");
        assertThat(embedder.normalized()).isTrue();
    }
}
