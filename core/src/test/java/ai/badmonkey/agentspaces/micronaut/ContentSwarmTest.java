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
package ai.badmonkey.agentspaces.micronaut;

import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.agent.content.Manifest;
import ai.badmonkey.agentspaces.console.ContentPanel;
import ai.badmonkey.agentspaces.content.Content;
import ai.badmonkey.agentspaces.content.Fetch;
import ai.badmonkey.agentspaces.content.PublishOptions;
import ai.badmonkey.agentspaces.micronaut.support.Contexts;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The content swarm under Micronaut (srad/large-networks-bulk-data.md §7.2): the
 * {@code agentspaces.content.*} block binds through the shared wiring, the group provides the
 * swarm, two contexts publish and fetch, and the console panel appears when the console is on.
 */
class ContentSwarmTest {

    private static Map<String, Object> content(Map<String, Object> more) {
        Map<String, Object> all = new HashMap<>(more);
        all.put("agentspaces.content.enabled", "true");
        all.put("agentspaces.content.piece-size", "65536");
        all.put("agentspaces.content.store.backends[0]", "memory");
        return all;
    }

    private static Content swarm(ApplicationContext ctx) {
        return ctx.getBean(AgentSpaces.class).group("it-fleet").provider(Content.TYPE)
                .map(Content.class::cast).orElseThrow();
    }

    @Test
    @Timeout(90)
    void twoContextsPublishAndFetchAndTheConsoleShowsThePanel() throws Exception {
        int seedPort = Contexts.freePort();
        try (ApplicationContext a = Contexts.run(Contexts.fleet(seedPort, 0), content(Map.of(
                     "test.app", "console", "agentspaces.console.enabled", "true", "agentspaces.console.port", "0")));
             ApplicationContext b = Contexts.run(Contexts.fleet(Contexts.freePort(), seedPort), content(Map.of()))) {
            assertThat(a.containsBean(ContentPanel.class)).isTrue();
            assertThat(b.containsBean(ContentPanel.class)).as("no console, no panel").isFalse();
            byte[] data = new byte[2 * 65536 + 100];
            new Random(29).nextBytes(data);
            Manifest manifest = swarm(a).publish(data, PublishOptions.defaults().withName("checkpoint.bin"));
            Fetch fetch = swarm(b).fetch(manifest.root());
            assertThat(fetch.await(Duration.ofSeconds(60))).as(fetch.trace().toString()).isTrue();
            assertThat(fetch.progress().bytesReceived()).isEqualTo(data.length);
            assertThat(swarm(b).describe(manifest.root())).isPresent();
        }
    }

    @Test
    void offByDefault() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), Map.of())) {
            assertThat(ctx.getBean(AgentSpaces.class).group("it-fleet").provider(Content.TYPE)).isEmpty();
            assertThat(ctx.containsBean(ContentPanel.class)).isFalse();
        }
    }
}
