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

import io.micronaut.runtime.Micronaut;

/**
 * The gateway peer: an MCP server over HTTP at {@code /mcp} that publishes the
 * fleet's tools to any MCP client, kept in step with the cards. Environment
 * {@code gateway}.
 */
public final class GatewayApp {

    private GatewayApp() {
    }

    public static void main(String[] args) {
        Micronaut.build(args).environments("gateway").start();
    }
}
