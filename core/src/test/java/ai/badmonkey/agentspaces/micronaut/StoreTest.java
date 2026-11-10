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

import ai.badmonkey.agentspaces.content.Content;
import ai.badmonkey.agentspaces.micronaut.store.ContentStores;
import ai.badmonkey.agentspaces.micronaut.support.Contexts;
import ai.badmonkey.agentspaces.pdt.Pdt;
import ai.badmonkey.agentspaces.pdt.values.Codec;
import ai.badmonkey.agentspaces.pdt.values.PMap;
import ai.badmonkey.agentspaces.planner.StoreChooser;
import ai.badmonkey.agentspaces.store.ContentStore;
import ai.badmonkey.agentspaces.store.StorePolicy;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The content store under Micronaut (PDT 2 phase 3, P2-S-10; srad/agentspaces-pdt2.md §7.2): the
 * {@code agentspaces.store.*} keys the Spring starter reads make a peer a store over its group's facade, its swarm
 * advertises the PDT feature level 2, a member's pin reaches it over TCP, and a store without the facade refuses
 * to start naming the keys.
 */
class StoreTest {

    private static final Codec<String> STR = Codec.of(String.class);

    private static Map<String, Object> pdt(boolean store) {
        Map<String, Object> all = new HashMap<>();
        all.put("agentspaces.content.enabled", "true");
        all.put("agentspaces.content.store.backends[0]", "memory");
        all.put("agentspaces.groups[0].spaces[3].name", "refs");
        all.put("agentspaces.groups[0].spaces[3].settle-window-millis", "100");
        all.put("agentspaces.pdt.enabled", "true");
        all.put("agentspaces.pdt.store.kind", "memory");
        all.put("agentspaces.pdt.space", "refs");
        if (store) {
            all.put("agentspaces.store.enabled", "true");
            all.put("agentspaces.store.name", "site-m");
            all.put("agentspaces.store.capacity", "1GiB");
            all.put("agentspaces.store.accept-requesters", "open");
            all.put("agentspaces.store.copies-max", "2");
        }
        return all;
    }

    @Test
    @Timeout(120)
    void aStoreContextTakesAMembersPinOverTcp() {
        int seedPort = Contexts.freePort();
        try (ApplicationContext a = Contexts.run(Contexts.fleet(seedPort, 0), pdt(true));
             ApplicationContext b = Contexts.run(Contexts.fleet(Contexts.freePort(), seedPort), pdt(false))) {
            ContentStore store = a.getBean(ContentStore.class);
            assertThat(store.assetName()).isEqualTo("content-store:site-m");
            assertThat(store.policy().copiesMax()).as("the keys reach the policy").isEqualTo(2);
            assertThat(store.policy().acceptRequesters()).isEqualTo(StorePolicy.Accept.OPEN);
            assertThat(a.getBean(ContentStores.class).find("it-fleet")).containsSame(store);
            assertThat(b.findBean(ContentStores.class)).as("a member that is no store has none").isEmpty();
            Content swarm = (Content) a.getBean(ai.badmonkey.agentspaces.agent.AgentSpaces.class).group("it-fleet")
                    .provider(Content.TYPE).orElseThrow();
            assertThat(swarm.pdtLevel()).isEqualTo(Content.PDT_LEVEL);

            Pdt member = b.getBean(Pdt.class);
            PMap<String, String> value = member.mapRef("ledger", STR, STR).swap(m -> m.assoc("invoice-1", "paid"));
            assertThat(Contexts.await(Duration.ofSeconds(60), () -> {
                try {
                    member.storeFor(value.root(), StoreChooser.Need.of(0, 1), Duration.ofHours(1));
                    return true;
                } catch (IllegalStateException noCardYet) {
                    return false;
                }
            })).as("the store's card reached the member").isTrue();
            assertThat(Contexts.await(Duration.ofSeconds(60), () -> store.ledger().holds(value.root())))
                    .as(store.status().toString()).isTrue();
            assertThat(Contexts.await(Duration.ofSeconds(30), () -> member.stores().receipts(value.root()).size() == 1)).isTrue();
        }
    }

    @Test
    @Timeout(60)
    void aStoreWithoutTheFacadeRefusesToStartNamingTheKeys() {
        Map<String, Object> noPdt = pdt(true);
        noPdt.put("agentspaces.pdt.enabled", "false");
        assertThatThrownBy(() -> Contexts.run(Contexts.fleet(Contexts.freePort(), 0), noPdt).close())
                .satisfies(e -> {
                    StringBuilder chain = new StringBuilder();
                    for (Throwable c = e; c != null; c = c.getCause()) {
                        chain.append(c.getMessage()).append(" <- ");
                    }
                    assertThat(chain.toString()).contains("agentspaces.store.enabled=true").contains("agentspaces.pdt.enabled=true");
                });
    }
}
