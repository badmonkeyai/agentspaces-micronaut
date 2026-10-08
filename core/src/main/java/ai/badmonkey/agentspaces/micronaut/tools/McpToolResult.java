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
package ai.badmonkey.agentspaces.micronaut.tools;

/**
 * The result of an imported MCP tool call.
 *
 * @param server the MCP client key
 * @param tool   the tool's name
 * @param callId the call's id
 * @param text   the tool's text result
 * @param error  whether the server reported an error
 */
public record McpToolResult(String server, String tool, String callId, String text, boolean error) {
}
