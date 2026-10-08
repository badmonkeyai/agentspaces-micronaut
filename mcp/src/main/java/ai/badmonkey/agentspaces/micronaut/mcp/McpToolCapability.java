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

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Singleton;

/**
 * Makes the MCP server advertise the tools capability even when it has no
 * tools of its own at build time: the SDK refuses {@code addTool} on a server
 * built without it, and the fleet's tools arrive after startup, as cards do.
 */
@Singleton
@Requires(property = "agentspaces.micronaut.mcp.export.enabled", value = "true")
@Requires(classes = McpSchema.ServerCapabilities.Builder.class)
public class McpToolCapability implements BeanCreatedEventListener<McpSchema.ServerCapabilities.Builder> {

    private final boolean listChanged;

    public McpToolCapability(@Property(name = "micronaut.mcp.server.tools.list-changed", defaultValue = "false")
                             boolean listChanged) {
        this.listChanged = listChanged;
    }

    @Override
    public McpSchema.ServerCapabilities.Builder onCreated(BeanCreatedEvent<McpSchema.ServerCapabilities.Builder> event) {
        return event.getBean().tools(listChanged);
    }
}
