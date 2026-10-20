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
import ai.badmonkey.agentspaces.agent.annotation.SpaceTake;
import ai.badmonkey.agentspaces.api.space.Lease;
import ai.badmonkey.agentspaces.api.space.Template;
import ai.badmonkey.agentspaces.micronaut.enroll.FleetEnroller;
import ai.badmonkey.agentspaces.micronaut.support.Contexts;
import ai.badmonkey.agentspaces.micronaut.support.ItFinding;
import ai.badmonkey.agentspaces.micronaut.support.ItTask;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The README's "How to use AI with AgentSpaces" section, verbatim: every
 * documentation snippet is cut from checked-in code that runs under the gate
 * (FEATURES.md section 15, item 18). The lines between each pair of
 * {@code // readme:} markers are the snippet the README shows, with the
 * imports elided. The bean below is a plain {@code @Singleton} whose only
 * fleet annotation is on a method, so it enrolls through the processor this
 * module's tests compile with. Two application contexts form one fleet over
 * TCP on free loopback ports, with no network and no model key;
 * {@code Contexts.fleet(port, seedPort)} is the README's YAML as a property
 * map (one founded group, {@code it-fleet}, with the {@code tasks},
 * {@code findings}, and {@code data} spaces).
 */
class ReadmeExamplesTest {

    // readme: begin agent-class
    @Singleton                                                       // a plain bean: the processor enrolls it at compile time
    @Requires(property = "test.app", value = "readme-summarizer")
    public static class Summarizer {

        @SpaceTake(space = "tasks", lease = "1m", pollTimeout = "PT0.2S", resultSpace = "findings")
        public ItFinding summarize(ItTask task) {
            return new ItFinding(task.topic(), "summary of " + task.topic());
        }
    }
    // readme: end agent-class

    @Test
    @Timeout(120)
    void anAgentOnOnePeerTakesWhatAnotherPeerWrites() {
        // readme: begin agent-run
        int workerPort = Contexts.freePort();
        try (ApplicationContext worker = Contexts.run(Contexts.fleet(workerPort, 0), Map.of("test.app", "readme-summarizer"));
             ApplicationContext writer = Contexts.run(Contexts.fleet(Contexts.freePort(), workerPort), Map.of())) {
            assertThat(worker.getBean(FleetEnroller.class).enrolled()).singleElement().isInstanceOf(Summarizer.class);

            AgentSpaces.GroupContext fleet = writer.getBean(AgentSpaces.class).group("it-fleet");
            fleet.space("tasks").write(new ItTask("tuple spaces", 1), Lease.of(Duration.ofMinutes(10)));
            Optional<ItFinding> finding = fleet.space("findings").read(Template.of(ItFinding.class), Duration.ofSeconds(60));
            assertThat(finding).hasValueSatisfying(f -> assertThat(f.summary()).isEqualTo("summary of tuple spaces"));
        }
        // readme: end agent-run
    }
}
