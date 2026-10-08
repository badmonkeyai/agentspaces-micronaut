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
import ai.badmonkey.agentspaces.micronaut.FleetParticipant;
import ai.badmonkey.agentspaces.spring.AgentSpacesProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.Objects;

/** Serves the A2A gateway with the fabric on {@code agentspaces.a2a.bind:port}. */
@Singleton
@Order(210)
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.a2a.enabled", value = "true")
@Requires(classes = A2aGateway.class)
public class A2aLifecycle implements FleetParticipant {

    private final A2aGateway gateway;
    private final String bind;
    private final int port;
    private boolean running;

    public A2aLifecycle(AgentSpacesProperties properties, A2aGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.bind = properties.getA2a().getBind();
        this.port = properties.getA2a().getPort();
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        try {
            gateway.start(new InetSocketAddress(bind, port));
        } catch (IOException e) {
            throw new UncheckedIOException("A2A gateway " + bind + ":" + port + " unavailable", e);
        }
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        gateway.close();
    }

    /** @return the port the gateway bound, or 0 before start */
    public synchronized int boundPort() {
        return running ? gateway.port() : 0;
    }
}
