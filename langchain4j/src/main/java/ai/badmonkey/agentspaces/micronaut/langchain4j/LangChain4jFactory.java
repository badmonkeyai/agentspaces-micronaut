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

import ai.badmonkey.agentspaces.api.spi.Authorizer;
import ai.badmonkey.agentspaces.api.spi.Embedder;
import ai.badmonkey.agentspaces.capabilities.aggregate.PushSumAggregate;
import ai.badmonkey.agentspaces.micronaut.FleetContext;
import ai.badmonkey.agentspaces.micronaut.langchain4j.embed.LangChain4jEmbedder;
import ai.badmonkey.agentspaces.micronaut.langchain4j.memory.ConversationIdResolver;
import ai.badmonkey.agentspaces.micronaut.langchain4j.memory.FleetChatMemoryProvider;
import ai.badmonkey.agentspaces.micronaut.langchain4j.memory.SpaceChatMemoryStore;
import ai.badmonkey.agentspaces.micronaut.langchain4j.model.FleetChatModel;
import ai.badmonkey.agentspaces.micronaut.langchain4j.model.FleetStreamingChatModel;
import ai.badmonkey.agentspaces.micronaut.langchain4j.model.ModelCatalog;
import ai.badmonkey.agentspaces.micronaut.langchain4j.model.ModelRequestRouter;
import ai.badmonkey.agentspaces.micronaut.langchain4j.model.ModelServer;
import ai.badmonkey.agentspaces.micronaut.langchain4j.model.StreamChunkingPolicy;
import ai.badmonkey.agentspaces.micronaut.langchain4j.model.StreamRestartPolicy;
import ai.badmonkey.agentspaces.micronaut.langchain4j.usage.FleetUsage;
import ai.badmonkey.agentspaces.micronaut.langchain4j.usage.FleetUsageListener;
import ai.badmonkey.agentspaces.micronaut.langchain4j.usage.FleetUsagePublisher;
import ai.badmonkey.agentspaces.micronaut.langchain4j.usage.UsagePanel;
import ai.badmonkey.agentspaces.micronaut.langchain4j.usage.UsageSink;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The LangChain4j bridge's beans, one group per feature, each behind its property. */
@Factory
@Requires(property = "agentspaces.enabled", notEquals = "false")
public class LangChain4jFactory {

    // ---------------------------------------------------------------- L3 memory

    @Singleton
    @Named("fleet")
    @Requires(property = "agentspaces.micronaut.langchain4j.memory.enabled", value = "true")
    public SpaceChatMemoryStore spaceChatMemoryStore(FleetContext fleet, LangChain4jSettings settings) {
        LangChain4jSettings.Memory memory = settings.getMemory();
        return new SpaceChatMemoryStore(
                fleet.requiredSpace(memory.getSpace(), "agentspaces.micronaut.langchain4j.memory.space"),
                memory.getTtl());
    }

    @Singleton
    @Requires(missingBeans = ConversationIdResolver.class)
    public ConversationIdResolver conversationIdResolver(LangChain4jSettings settings) {
        return ConversationIdResolver.of(settings.getMemory().getConversationId());
    }

    @Singleton
    @Named("fleet")
    @Requires(property = "agentspaces.micronaut.langchain4j.memory.enabled", value = "true")
    public FleetChatMemoryProvider fleetChatMemoryProvider(@Named("fleet") SpaceChatMemoryStore store,
                                                           ConversationIdResolver resolver,
                                                           LangChain4jSettings settings) {
        return new FleetChatMemoryProvider(store, resolver, settings.getMemory().getMaxMessages());
    }

    // ---------------------------------------------------------------- L4 model client

    @Singleton
    @Requires(missingBeans = StreamRestartPolicy.class)
    public StreamRestartPolicy streamRestartPolicy(LangChain4jSettings settings) {
        return StreamRestartPolicy.of(settings.getModelClient().getStream().getOnRestart());
    }

    @Singleton
    @Secondary
    @Named("fleet")
    @Requires(property = "agentspaces.micronaut.langchain4j.model-client.enabled", value = "true")
    public FleetChatModel fleetChatModel(FleetContext fleet, LangChain4jSettings settings, StreamRestartPolicy restart,
                                         @Named(TaskExecutors.SCHEDULED) TaskScheduler scheduler,
                                         List<ChatModelListener> listeners) {
        LangChain4jSettings.ModelClient client = settings.getModelClient();
        return new FleetChatModel(
                fleet.requiredSpace(client.getSpace(), "agentspaces.micronaut.langchain4j.model-client.space"),
                fleet.authorizer(), fleet.peerId().value(), client.getModel(), client.getTimeout(), restart,
                scheduler, listeners);
    }

    @Singleton
    @Secondary
    @Named("fleet")
    @Requires(property = "agentspaces.micronaut.langchain4j.model-client.enabled", value = "true")
    public FleetStreamingChatModel fleetStreamingChatModel(FleetChatModel model) {
        return new FleetStreamingChatModel(model);
    }

    // ---------------------------------------------------------------- L4 model server

    @Singleton
    @Requires(property = "agentspaces.micronaut.langchain4j.model-server.enabled", value = "true")
    @Requires(missingBeans = ModelCatalog.class)
    public ModelCatalog modelCatalog(LangChain4jSettings settings, Map<String, ChatModel> models,
                                     Map<String, StreamingChatModel> streaming) {
        Map<String, ChatModel> providers = new LinkedHashMap<>();
        models.forEach((name, model) -> {
            if (!(model instanceof FleetChatModel)) {
                providers.put(name, model);
            }
        });
        Map<String, StreamingChatModel> streamingProviders = new LinkedHashMap<>();
        streaming.forEach((name, model) -> {
            if (!(model instanceof FleetChatModel)) {
                streamingProviders.put(name, model);
            }
        });
        LangChain4jSettings.ModelServer server = settings.getModelServer();
        return ModelCatalog.of(server.getModels(), providers, streamingProviders, server.getModelBeans());
    }

    @Singleton
    @Requires(property = "agentspaces.micronaut.langchain4j.model-server.enabled", value = "true")
    @Requires(missingBeans = ModelRequestRouter.class)
    public ModelRequestRouter modelRequestRouter(LangChain4jSettings settings) {
        LangChain4jSettings.ModelServer server = settings.getModelServer();
        return server.getRouting() == LangChain4jSettings.Routing.PRICE
                ? ModelRequestRouter.byPrice(server.getPrices()) : ModelRequestRouter.byModel();
    }

    @Singleton
    @Requires(property = "agentspaces.micronaut.langchain4j.model-server.enabled", value = "true")
    @Requires(missingBeans = StreamChunkingPolicy.class)
    public StreamChunkingPolicy streamChunkingPolicy(LangChain4jSettings settings) {
        LangChain4jSettings.ModelServer server = settings.getModelServer();
        return StreamChunkingPolicy.of(server.getChunkInterval(), server.getChunkMaxDeltas());
    }

    @Singleton
    @Requires(property = "agentspaces.micronaut.langchain4j.model-server.enabled", value = "true")
    public ModelServer modelServer(FleetContext fleet, ModelCatalog catalog, ModelRequestRouter router,
                                   StreamChunkingPolicy chunking,
                                   @Named(TaskExecutors.SCHEDULED) TaskScheduler scheduler,
                                   LangChain4jSettings settings) {
        LangChain4jSettings.ModelServer server = settings.getModelServer();
        for (String model : catalog.models()) {
            if (!fleet.authorizer().permits(fleet.peerId(), Authorizer.Operation.MODEL_SERVE, model)) {
                LoggerFactory.getLogger(ModelServer.class).warn("this peer is not permitted MODEL_SERVE for '{}';"
                        + " callers will ignore its answers (agentspaces.security.grants.model-serve)", model);
            }
        }
        return new ModelServer(
                fleet.requiredSpace(server.getSpace(), "agentspaces.micronaut.langchain4j.model-server.space"),
                catalog, router, chunking, scheduler, fleet.peerId().value(),
                new ModelServer.Settings(server.getLease(), server.getChunkLease(), server.getResponseLease(),
                        server.getConcurrency()));
    }

    // ---------------------------------------------------------------- L5 embeddings

    @Singleton
    @Requires(property = "agentspaces.micronaut.langchain4j.embedder.type", value = "langchain4j")
    public Embedder langChain4jEmbedder(@Nullable EmbeddingModel model, LangChain4jSettings settings) {
        if (model == null) {
            throw new IllegalStateException("agentspaces.micronaut.langchain4j.embedder.type=langchain4j needs an"
                    + " EmbeddingModel bean; add a micronaut-langchain4j provider with an embedding model, or set"
                    + " the type to hashing");
        }
        LangChain4jSettings.Embedder embedder = settings.getEmbedder();
        return new LangChain4jEmbedder(model, embedder.getModel(), embedder.getCacheSize());
    }

    // ---------------------------------------------------------------- L6 usage

    @Singleton
    @Requires(property = "agentspaces.micronaut.langchain4j.usage.enabled", value = "true")
    public FleetUsage fleetUsage(FleetContext fleet, LangChain4jSettings settings) {
        LangChain4jSettings.Usage usage = settings.getUsage();
        PushSumAggregate aggregate = fleet.group().provider(PushSumAggregate.TYPE)
                .filter(PushSumAggregate.class::isInstance).map(PushSumAggregate.class::cast).orElse(null);
        return new FleetUsage(aggregate,
                usage.getMetricsSpace().isBlank() ? null
                        : fleet.requiredSpace(usage.getMetricsSpace(), "agentspaces.micronaut.langchain4j.usage.metrics-space"),
                usage.getEntryLease(), usage.getPublishInterval(), Clock.systemUTC());
    }

    @Singleton
    @Requires(property = "agentspaces.micronaut.langchain4j.usage.enabled", value = "true")
    @Requires(missingBeans = UsageSink.class)
    public UsageSink usageSink(FleetUsage usage) {
        return usage;
    }

    @Singleton
    @Requires(property = "agentspaces.micronaut.langchain4j.usage.enabled", value = "true")
    public FleetUsageListener fleetUsageListener(UsageSink sink, FleetContext fleet) {
        return new FleetUsageListener(sink, fleet.peerId().value());
    }

    @Singleton
    @Requires(property = "agentspaces.micronaut.langchain4j.usage.enabled", value = "true")
    public FleetUsagePublisher fleetUsagePublisher(FleetUsage usage, @Named(TaskExecutors.SCHEDULED) TaskScheduler scheduler,
                                                   LangChain4jSettings settings) {
        return new FleetUsagePublisher(usage, scheduler, settings.getUsage().getPublishInterval());
    }

    @Singleton
    @Requires(property = "agentspaces.micronaut.langchain4j.usage.enabled", value = "true")
    public UsagePanel usagePanel(FleetUsage usage) {
        return new UsagePanel(usage);
    }
}
