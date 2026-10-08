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

import ai.badmonkey.agentspaces.micronaut.management.FleetView;
import ai.badmonkey.agentspaces.micronaut.support.Contexts;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementTest {

    @Test
    @Timeout(60)
    void healthInfoEndpointAndMetersShowTheFleet() {
        Map<String, Object> props = new HashMap<>(Contexts.fleet(Contexts.freePort(), 0));
        props.put("micronaut.server.port", "-1");
        props.put("micronaut.application.name", "orders");
        props.put("test.app", "researcher");
        props.put("endpoints.health.details-visible", "ANONYMOUS");
        props.put("endpoints.info.sensitive", "false");
        props.put("endpoints.agentspaces.sensitive", "false");
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, props);
        try (ApplicationContext ctx = server.getApplicationContext()) {
            String base = server.getURI().toString();
            FleetView view = ctx.getBean(FleetView.class);
            assertThat(view.summary()).containsEntry("running", true).containsEntry("enrolled-agents", 1);

            String health = Contexts.get(base + "/health");
            assertThat(health).contains("\"agentspaces\"").contains("\"UP\"").contains("\"it-fleet\"");

            String info = Contexts.get(base + "/info");
            assertThat(info).contains("\"agentspaces\"").contains(view.peerId().value());

            String fleet = Contexts.get(base + "/agentspaces");
            assertThat(fleet).contains("\"cards\"").contains("\"it-researcher\"");

            MeterRegistry meters = ctx.getBean(MeterRegistry.class);
            assertThat(meters.find("agentspaces.members").tag("group", "it-fleet").gauge()).isNotNull();
            assertThat(meters.find("agentspaces.agents.enrolled").gauge().value()).isEqualTo(1.0);
        }
    }

    @Test
    void managementCanBeSwitchedOff() {
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0),
                Map.of("agentspaces.micronaut.management.enabled", "false"))) {
            assertThat(ctx.containsBean(FleetView.class)).isFalse();
        }
    }
}
