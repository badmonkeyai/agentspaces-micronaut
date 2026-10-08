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
package ai.badmonkey.agentspaces.micronaut.langchain4j.worker;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Attaches every {@code ChatModelListener} bean (the take-lease and usage
 * listeners) to the provider builders micronaut-langchain4j injects them into
 * on its own only for OpenAI. One listener per provider module on the
 * classpath; any other provider needs the same one-liner in the application.
 */
public final class ProviderBuilderListeners {

    private ProviderBuilderListeners() {
    }

    /** Ollama chat model builders. */
    @Singleton
    @Requires(classes = OllamaChatModel.OllamaChatModelBuilder.class)
    @Requires(beans = ChatModelListener.class)
    public static class Ollama implements BeanCreatedEventListener<OllamaChatModel.OllamaChatModelBuilder> {
        private final List<ChatModelListener> listeners;

        public Ollama(List<ChatModelListener> listeners) {
            this.listeners = listeners;
        }

        @Override
        public OllamaChatModel.OllamaChatModelBuilder onCreated(
                BeanCreatedEvent<OllamaChatModel.OllamaChatModelBuilder> event) {
            return event.getBean().listeners(listeners);
        }
    }

    /** Anthropic chat model builders. */
    @Singleton
    @Requires(classes = AnthropicChatModel.AnthropicChatModelBuilder.class)
    @Requires(beans = ChatModelListener.class)
    public static class Anthropic implements BeanCreatedEventListener<AnthropicChatModel.AnthropicChatModelBuilder> {
        private final List<ChatModelListener> listeners;

        public Anthropic(List<ChatModelListener> listeners) {
            this.listeners = listeners;
        }

        @Override
        public AnthropicChatModel.AnthropicChatModelBuilder onCreated(
                BeanCreatedEvent<AnthropicChatModel.AnthropicChatModelBuilder> event) {
            return event.getBean().listeners(listeners);
        }
    }
}
