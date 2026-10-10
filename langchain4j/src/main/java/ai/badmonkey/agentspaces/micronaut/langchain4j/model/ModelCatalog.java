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
package ai.badmonkey.agentspaces.micronaut.langchain4j.model;

/**
 * As this bridge first named it; the type now lives in {@code agentspaces-langchain4j}.
 * A bean of this type still replaces the default.
 *
 * @deprecated use {@link ai.badmonkey.agentspaces.langchain4j.model.ModelCatalog}; this alias goes in the next release
 */
@Deprecated(since = "0.2.0", forRemoval = true)
public interface ModelCatalog extends ai.badmonkey.agentspaces.langchain4j.model.ModelCatalog {

    static ai.badmonkey.agentspaces.langchain4j.model.ModelCatalog of(
            java.util.List<String> models, java.util.Map<String, dev.langchain4j.model.chat.ChatModel> beans,
            java.util.Map<String, dev.langchain4j.model.chat.StreamingChatModel> streamingBeans,
            java.util.Map<String, String> modelBeans) {
        return ai.badmonkey.agentspaces.langchain4j.model.ModelCatalog.of(models, beans, streamingBeans, modelBeans);
    }

    static String modelName(dev.langchain4j.model.chat.ChatModel model) {
        return ai.badmonkey.agentspaces.langchain4j.model.ModelCatalog.modelName(model);
    }
}
