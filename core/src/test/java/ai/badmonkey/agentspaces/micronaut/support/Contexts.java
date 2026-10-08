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
package ai.badmonkey.agentspaces.micronaut.support;

import io.micronaut.context.ApplicationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.HashMap;
import java.util.Map;

/** Builds test contexts the way the Spring integration test builds applications. */
public final class Contexts {

    private Contexts() {
    }

    public static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The it-fleet property set: one founded group, tasks and findings spaces. */
    public static Map<String, Object> fleet(int port, int seedPort) {
        Map<String, Object> props = new HashMap<>();
        props.put("agentspaces.bind", "127.0.0.1:" + port);
        props.put("agentspaces.tick-millis", "200");
        props.put("agentspaces.groups[0].name", "it-fleet");
        props.put("agentspaces.groups[0].founding", "micronaut-it-fleet-v1");
        props.put("agentspaces.groups[0].spaces[0].name", "tasks");
        props.put("agentspaces.groups[0].spaces[0].settle-window-millis", "100");
        props.put("agentspaces.groups[0].spaces[1].name", "findings");
        props.put("agentspaces.groups[0].spaces[1].settle-window-millis", "100");
        props.put("agentspaces.groups[0].spaces[2].name", "data");
        props.put("agentspaces.groups[0].spaces[2].settle-window-millis", "100");
        if (seedPort > 0) {
            props.put("agentspaces.groups[0].seeds[0]", "127.0.0.1:" + seedPort);
        }
        return props;
    }

    /** Polls until the condition holds or the timeout passes. */
    public static boolean await(Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    /** A GET through the JDK client, returning the body; status must be 2xx. */
    public static String get(String url) {
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("GET " + url + " -> " + response.statusCode()
                        + ": " + response.body());
            }
            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public static ApplicationContext run(Map<String, Object> props, Map<String, Object> more) {
        Map<String, Object> all = new HashMap<>(props);
        all.putAll(more);
        return ApplicationContext.run(all);
    }
}
