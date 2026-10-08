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
package ai.badmonkey.agentspaces.micronaut.a2a;

import ai.badmonkey.agentspaces.a2a.A2aGateway;
import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.spring.AgentSpacesA2aAutoConfiguration;
import ai.badmonkey.agentspaces.spring.AgentSpacesProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * The A2A gateway ({@code agentspaces.a2a.enabled=true}, with
 * {@code agentspaces-a2a} on the classpath) built by the Spring starter's
 * build method, which keeps the fail-fast rule: a routable bind with neither
 * a token nor an OIDC gate refuses to start.
 */
@Factory
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.a2a.enabled", value = "true")
@Requires(classes = A2aGateway.class)
public class A2aFactory {

    @Singleton
    @Requires(missingBeans = A2aGateway.class)
    public A2aGateway a2aGateway(AgentSpacesProperties properties, AgentSpaces spaces) {
        return new AgentSpacesA2aAutoConfiguration().agentSpacesA2aGateway(properties, spaces);
    }
}
