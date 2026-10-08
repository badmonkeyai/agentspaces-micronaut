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
package ai.badmonkey.agentspaces.examples.micronaut;

import ai.badmonkey.agentspaces.api.ad.AgentCard;
import ai.badmonkey.agentspaces.examples.micronaut.analyst.AnalystRun;
import ai.badmonkey.agentspaces.examples.micronaut.analyst.FindingsWatcher;
import ai.badmonkey.agentspaces.micronaut.FleetContext;
import ai.badmonkey.agentspaces.micronaut.enroll.FleetEnroller;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Both services in one JVM over TCP on free ports: the whole loop of example 16. */
class MicronautFleetTest {

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean await(Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && !condition.getAsBoolean()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    @Test
    @Timeout(180)
    void theAnalystDrivesTheOrdersServiceThreeWays() {
        int ordersPort = freePort();
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "micronaut.server.port", "-1",
                "agentspaces.bind", "127.0.0.1:" + ordersPort), "orders");
        try (ApplicationContext orders = server.getApplicationContext();
             ApplicationContext analyst = ApplicationContext.run(Map.of(
                     "agentspaces.bind", "127.0.0.1:" + freePort(),
                     "agentspaces.groups[0].seeds[0]", "127.0.0.1:" + ordersPort), "analyst")) {

            // The controller enrolled as an agent, the watcher through the processor.
            assertThat(orders.getBean(FleetEnroller.class).enrolled()).hasSize(1);
            assertThat(analyst.getBean(FleetEnroller.class).enrolled())
                    .singleElement().isInstanceOf(FindingsWatcher.class);

            AnalystRun run = analyst.getBean(AnalystRun.class);
            assertThat(await(Duration.ofSeconds(60), run::ordersServiceVisible)).isTrue();
            FleetContext fleet = analyst.getBean(FleetContext.class);
            assertThat(fleet.group().discovery().find(AgentCard.class,
                    card -> "orders-service".equals(card.agent().localName())))
                    .singleElement()
                    .satisfies(card -> assertThat(card.actions()).extracting("name").contains("price"));

            // 1. Through the task space: the controller method takes the request.
            run.requestPrice("1");
            FindingsWatcher watcher = analyst.getBean(FindingsWatcher.class);
            assertThat(await(Duration.ofSeconds(60), () -> !watcher.seen().isEmpty())).isTrue();
            assertThat(watcher.seen().get(0).total()).isEqualTo(28.5);

            // 2. Through the data space: the orders table is an asset.
            assertThat(run.fetchOrders()).hasValueSatisfying(fetched ->
                    assertThat(fetched.result().rows()).hasSize(3));

            // 3. Over HTTP through the fleet registry: no URL configured anywhere.
            assertThat(run.describe("2")).isEqualTo("order 2: 1 x gadget");

            // The service's management views see the fleet.
            assertThat(get(server.getURI() + "/agentspaces"))
                    .contains("orders-service").contains("\"asset\":\"orders\"").contains("\"asset\":\"orders-table\"");
        }
    }

    private static String get(String url) {
        try {
            return java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
