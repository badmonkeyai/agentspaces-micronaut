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
package ai.badmonkey.agentspaces.micronaut.langchain4j.tools;

import ai.badmonkey.agentspaces.micronaut.langchain4j.LangChain4jSettings;
import ai.badmonkey.agentspaces.langchain4j.memory.FleetChatMemoryProvider;
import ai.badmonkey.agentspaces.langchain4j.tools.FleetToolProvider;
import dev.langchain4j.service.AiServices;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gives every {@code @AiService} the fleet: micronaut-langchain4j builds each
 * service through an {@code AiServices} bean, and never looks up a
 * {@code ToolProvider} on its own, so this listener applies the fleet tool
 * provider ({@code tools.auto-apply}) and, when asked, the fleet chat memory
 * ({@code memory.auto-apply}) as each builder is created.
 */
@Singleton
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(beans = FleetToolProvider.class)
public class FleetAiServicesListener implements BeanCreatedEventListener<AiServices<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(FleetAiServicesListener.class);

    private final FleetToolProvider tools;
    private final FleetChatMemoryProvider memory;
    private final boolean applyTools;
    private final boolean applyMemory;

    public FleetAiServicesListener(FleetToolProvider tools, @Nullable FleetChatMemoryProvider memory,
                                   LangChain4jSettings settings) {
        this.tools = tools;
        this.memory = memory;
        this.applyTools = settings.getTools().isAutoApply();
        this.applyMemory = settings.getMemory().isAutoApply() && memory != null;
    }

    @Override
    public AiServices<?> onCreated(BeanCreatedEvent<AiServices<?>> event) {
        AiServices<?> builder = event.getBean();
        if (applyTools) {
            builder.toolProvider(tools);
            LOG.debug("fleet tools applied to an AiService");
        }
        if (applyMemory) {
            builder.chatMemoryProvider(memory);
        }
        return builder;
    }
}
