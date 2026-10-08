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
package ai.badmonkey.agentspaces.micronaut.mcp;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

/**
 * Gives each micronaut-mcp LangChain4j client the name of its configuration
 * ({@code micronaut.mcp.client.http.<name>}, or {@code stdio}) as its key:
 * LangChain4j's default is a random UUID, and the key names the fleet agent
 * ({@code mcp-<key>}) and the {@code server} field callers write.
 */
@Singleton
@Requires(property = "agentspaces.micronaut.mcp.import.enabled", value = "true")
@Requires(classes = DefaultMcpClient.Builder.class)
public class McpClientKeys implements BeanCreatedEventListener<DefaultMcpClient.Builder> {

    @Override
    public DefaultMcpClient.Builder onCreated(BeanCreatedEvent<DefaultMcpClient.Builder> event) {
        String name = event.getBeanIdentifier().getName();
        return name == null || name.isBlank() ? event.getBean() : event.getBean().key(name);
    }
}
