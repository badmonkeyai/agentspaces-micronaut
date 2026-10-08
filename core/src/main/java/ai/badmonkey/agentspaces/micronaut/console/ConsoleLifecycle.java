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
package ai.badmonkey.agentspaces.micronaut.console;

import ai.badmonkey.agentspaces.console.FleetConsoleServer;
import ai.badmonkey.agentspaces.micronaut.FleetParticipant;
import ai.badmonkey.agentspaces.spring.AgentSpacesProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/** Serves the console with the fabric: started after agents enroll, closed before the node. */
@Singleton
@Order(200)
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.console.enabled", value = "true")
public class ConsoleLifecycle implements FleetParticipant {

    private final FleetConsoleServer server;
    private final int port;
    private boolean running;

    public ConsoleLifecycle(AgentSpacesProperties properties, FleetConsoleServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.port = properties.getConsole().getPort();
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        try {
            server.start(port);
        } catch (IOException e) {
            throw new UncheckedIOException("console port " + port + " unavailable", e);
        }
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        server.close();
    }

    /** @return the port the console bound, or 0 before start */
    public synchronized int boundPort() {
        return running ? server.port() : 0;
    }
}
