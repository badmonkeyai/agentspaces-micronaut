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

import ai.badmonkey.agentspaces.micronaut.pdt.PdtSettings;
import ai.badmonkey.agentspaces.micronaut.pdt.PdtStores;
import ai.badmonkey.agentspaces.micronaut.pdt.Pdts;
import ai.badmonkey.agentspaces.micronaut.pdt.RefSettings;
import ai.badmonkey.agentspaces.micronaut.support.Contexts;
import ai.badmonkey.agentspaces.pdt.NodeStore;
import ai.badmonkey.agentspaces.pdt.Pdt;
import ai.badmonkey.agentspaces.pdt.refs.Ref;
import ai.badmonkey.agentspaces.pdt.values.Codec;
import ai.badmonkey.agentspaces.pdt.values.PMap;
import ai.badmonkey.agentspaces.peering.blocks.MemoryBlockStore;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistent data trees under Micronaut (srad/agentspaces-pdt.md PD-C-03): the
 * {@code agentspaces.pdt.*} keys bind, a {@code Pdt} per group comes from the
 * factory, a {@code NodeStore} bean is honoured, and a swap on one context is
 * deref'd on the other over TCP, modelled on {@link ContentSwarmTest}.
 */
class PdtTest {

    private static final Codec<String> STR = Codec.of(String.class);

    /** The content swarm plus PDT over a registered {@code refs} space, on a memory store. */
    private static Map<String, Object> pdt(Map<String, Object> more) {
        Map<String, Object> all = new HashMap<>(more);
        all.put("agentspaces.content.enabled", "true");
        all.put("agentspaces.content.store.backends[0]", "memory");
        all.put("agentspaces.groups[0].spaces[3].name", "refs");
        all.put("agentspaces.groups[0].spaces[3].settle-window-millis", "100");
        all.put("agentspaces.pdt.enabled", "true");
        all.put("agentspaces.pdt.store.kind", "memory");
        all.put("agentspaces.pdt.cancel-grace", "2s");
        all.put("agentspaces.pdt.refs.ledger.mode", "multi");
        return all;
    }

    private static String causes(Throwable t) {
        StringBuilder chain = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            chain.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append(" <- ");
        }
        return chain.toString();
    }

    /** Starts a context with the properties and the singletons; the factory hands the shared wiring the group's node store (PD-C-03). */
    private static ApplicationContext start(Map<String, Object> props, Map<String, Object> more, NodeStore store) {
        Map<String, Object> all = new HashMap<>(props);
        all.putAll(more);
        ApplicationContext ctx = ApplicationContext.builder().properties(all).build();
        if (store != null) {
            ctx.registerSingleton(NodeStore.class, store);
        }
        try {
            return ctx.start();
        } catch (RuntimeException e) {
            ctx.close();
            throw e;
        }
    }

    private static boolean sameRoot(Ref<PMap<String, String>> ref, String root) {
        try {
            return root.equals(ref.deref().root());
        } catch (RuntimeException loading) {
            return false;
        }
    }

    @Test
    @Timeout(120)
    void aSwapOnOneContextIsDerefdOnTheOtherAndTheStoreBeanWins() {
        int seedPort = Contexts.freePort();
        NodeStore userStore = NodeStore.inMemory();
        try (ApplicationContext a = start(Contexts.fleet(seedPort, 0), pdt(Map.of()), null);
             ApplicationContext b = start(Contexts.fleet(Contexts.freePort(), seedPort), pdt(Map.of()), userStore)) {
            Pdt onA = a.getBean(Pdt.class);
            Pdt onB = b.getBean(Pdt.class);
            assertThat(a.getBean(Pdts.class).groups()).containsExactly("it-fleet");
            assertThat(a.getBean(PdtStores.class).isBean("it-fleet")).as("a built its store from the keys").isFalse();
            assertThat(b.getBean(PdtStores.class).isBean("it-fleet")).as("b took the NodeStore bean").isTrue();
            assertThat(onB.store()).isSameAs(userStore);
            assertThat(onA.refSpace().name()).isEqualTo("refs");

            Ref<PMap<String, String>> ledgerA = onA.mapRef("ledger", STR, STR);
            Ref<PMap<String, String>> ledgerB = onB.mapRef("ledger", STR, STR);
            PMap<String, String> v1 = ledgerA.swap(m -> m.assoc("invoice-1", "paid"));
            assertThat(Contexts.await(Duration.ofSeconds(60), () -> sameRoot(ledgerB, v1.root())))
                    .as("b deref'd a's head: " + onB.stats()).isTrue();
            assertThat(ledgerB.deref().get("invoice-1")).contains("paid");
            assertThat(onB.stats().deltasInline() + onB.stats().deltasFetched()).as("one delta reached b").isEqualTo(1);

            PMap<String, String> v2 = ledgerB.swap(m -> m.assoc("invoice-2", "open"));
            assertThat(Contexts.await(Duration.ofSeconds(60), () -> sameRoot(ledgerA, v2.root())))
                    .as("a deref'd b's head: " + onA.stats()).isTrue();
            assertThat(ledgerA.deref().count()).isEqualTo(2L);
            assertThat(ledgerA.deref().get("invoice-1")).contains("paid");
        }
    }

    /**
     * PD-P-05, PD-C-03: with a content key the factory hands the swarm's ring to the facade
     * (group-key sealing names the ring's epoch on every head), and sealed-nodes, which needs the
     * swarm built over the facade's sealed store, is refused by name rather than started keyless.
     */
    @Test
    @Timeout(120)
    void aContentKeyReachesTheFacadeAndSealedNodesIsRefusedByName() {
        String contentKey = java.util.Base64.getEncoder().encodeToString(
                ai.badmonkey.agentspaces.common.crypto.GroupKey.generate().rawBytes());
        Map<String, Object> groupKey = pdt(Map.of("agentspaces.pdt.sealing", "group-key",
                "agentspaces.groups[0].content-key", contentKey));
        try (ApplicationContext a = start(Contexts.fleet(Contexts.freePort(), 0), groupKey, null)) {
            Pdt onA = a.getBean(Pdt.class);
            assertThat(onA.sealing()).isEqualTo(ai.badmonkey.agentspaces.agent.pdt.PRef.SEALING_GROUP_KEY);
            assertThat(onA.refs().config().sealing()).isEqualTo(ai.badmonkey.agentspaces.agent.pdt.PRef.SEALING_GROUP_KEY);
            Ref<PMap<String, String>> ledgerA = onA.mapRef("ledger", STR, STR);
            ledgerA.swap(m -> m.assoc("invoice-1", "paid"));
            assertThat(ledgerA.deref().get("invoice-1")).contains("paid");
        }
        Map<String, Object> sealed = pdt(Map.of("agentspaces.pdt.sealing", "sealed-nodes",
                "agentspaces.groups[0].content-key", contentKey));
        assertThatThrownBy(() -> start(Contexts.fleet(Contexts.freePort(), 0), sealed, null).close())
                .satisfies(e -> assertThat(causes(e)).contains("sealed-nodes is not supported by the Micronaut factory"));
        // without a content key the configured sealing drops to none, as the Spring starter does
        Map<String, Object> keyless = pdt(Map.of("agentspaces.pdt.sealing", "sealed-nodes"));
        try (ApplicationContext a = start(Contexts.fleet(Contexts.freePort(), 0), keyless, null)) {
            assertThat(a.getBean(Pdt.class).sealing()).isEqualTo(ai.badmonkey.agentspaces.agent.pdt.PRef.SEALING_NONE);
        }
    }

    @Test
    void offByDefault() {
        Map<String, Object> content = new HashMap<>();
        content.put("agentspaces.content.enabled", "true");
        content.put("agentspaces.content.store.backends[0]", "memory");
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), content)) {
            assertThat(ctx.containsBean(Pdt.class)).isFalse();
            assertThat(ctx.containsBean(Pdts.class)).isFalse();
            assertThat(ctx.containsBean(PdtStores.class)).isFalse();
        }
    }

    @Test
    void refusesToStartWhenTheRefSpaceIsNotRegistered() {
        Map<String, Object> props = pdt(Map.of());
        props.remove("agentspaces.groups[0].spaces[3].name");
        props.remove("agentspaces.groups[0].spaces[3].settle-window-millis");
        assertThatThrownBy(() -> Contexts.run(Contexts.fleet(Contexts.freePort(), 0), props).close())
                .satisfies(e -> assertThat(causes(e)).contains("agentspaces.pdt.space=refs").contains("it-fleet"));
    }

    @Test
    void refusesToStartWithoutTheContentSwarm() {
        Map<String, Object> props = pdt(Map.of());
        props.put("agentspaces.content.enabled", "false");
        assertThatThrownBy(() -> Contexts.run(Contexts.fleet(Contexts.freePort(), 0), props).close())
                .satisfies(e -> assertThat(causes(e)).contains("agentspaces.content.enabled=true"));
    }

    @Test
    void aRequiredDurableStoreRefusesMemoryAndABlankPackPath() {
        Map<String, Object> memory = pdt(Map.of());
        memory.put("agentspaces.pdt.store.require", "true");
        assertThatThrownBy(() -> Contexts.run(Contexts.fleet(Contexts.freePort(), 0), memory).close())
                .satisfies(e -> assertThat(causes(e)).contains("agentspaces.pdt.store.require=true").contains("memory"));
        Map<String, Object> blank = pdt(Map.of());
        blank.put("agentspaces.pdt.store.kind", "pack");
        blank.put("agentspaces.pdt.store.path", "");
        blank.put("agentspaces.pdt.store.require", "true");
        assertThatThrownBy(() -> Contexts.run(Contexts.fleet(Contexts.freePort(), 0), blank).close())
                .satisfies(e -> assertThat(causes(e)).contains("agentspaces.pdt.store.require=true").contains("agentspaces.pdt.store.path"));
    }

    @Test
    void aBlankPackPathWithoutRequireFallsBackToTheMemoryStore() {
        Map<String, Object> blank = pdt(Map.of());
        blank.put("agentspaces.pdt.store.kind", "pack");
        blank.put("agentspaces.pdt.store.path", "");
        try (ApplicationContext ctx = Contexts.run(Contexts.fleet(Contexts.freePort(), 0), blank)) {
            // PD-Q-AVL-02: the facade is up on a memory store (the warning names agentspaces.pdt.store.path)
            assertThat(ctx.getBean(Pdt.class).store().backend()).isInstanceOf(MemoryBlockStore.class);
            assertThat(ctx.getBean(PdtStores.class).isBean("it-fleet")).isFalse();
        }
    }

    @Test
    void aBeanStoreKindWithoutABeanRefusesToStart() {
        Map<String, Object> props = pdt(Map.of());
        props.put("agentspaces.pdt.store.kind", "bean");
        assertThatThrownBy(() -> Contexts.run(Contexts.fleet(Contexts.freePort(), 0), props).close())
                .satisfies(e -> assertThat(causes(e)).contains("agentspaces.pdt.store.kind=bean").contains("NodeStore bean"));
    }

    @Test
    void theKeysBindWithTheStartersNamesAndDefaults() {
        Map<String, Object> props = new HashMap<>();
        props.put("agentspaces.enabled", "false");
        props.put("agentspaces.pdt.inline-delta", "16KB");
        props.put("agentspaces.pdt.load-timeout", "3s");
        props.put("agentspaces.pdt.follow", "lazy");
        props.put("agentspaces.pdt.store.kind", "Memory");
        props.put("agentspaces.pdt.store.quota", "2GB");
        props.put("agentspaces.pdt.retain.versions", "8");
        props.put("agentspaces.pdt.retain.age", "20m");
        props.put("agentspaces.pdt.take.retries", "5");
        props.put("agentspaces.pdt.refs.ledger.mode", "Linear");
        props.put("agentspaces.pdt.refs.facts.mode", "multi");
        try (ApplicationContext ctx = ApplicationContext.run(props)) {
            PdtSettings settings = ctx.getBean(PdtSettings.class);
            assertThat(settings.isEnabled()).isFalse();
            assertThat(settings.getSpace()).isEqualTo("refs");
            assertThat(settings.getInlineDelta()).isEqualTo(16 * 1024);
            assertThat(settings.getLoadTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(settings.getFollow()).isEqualTo(Pdt.FOLLOW_LAZY);
            assertThat(settings.getSealing()).isEqualTo("none");
            assertThat(settings.getHeadLease()).isEqualTo(Duration.ofMinutes(10));
            assertThat(settings.getStore().getKind()).isEqualTo("memory");
            assertThat(settings.getStore().getQuota()).isEqualTo(2L * 1024 * 1024 * 1024);
            assertThat(settings.getStore().isRequire()).isFalse();
            assertThat(settings.getRetain().getVersions()).isEqualTo(8);
            assertThat(settings.getRetain().getAge()).isEqualTo(Duration.ofMinutes(20));
            assertThat(settings.getTake().getRetries()).isEqualTo(5);
            assertThat(settings.getTake().getLease()).isEqualTo(Duration.ofSeconds(30));
            Collection<RefSettings> refs = ctx.getBeansOfType(RefSettings.class);
            assertThat(refs).extracting(RefSettings::getName).containsExactlyInAnyOrder("ledger", "facts");
            assertThat(refs).filteredOn(r -> r.getName().equals("ledger")).extracting(RefSettings::getMode)
                    .containsExactly("linear");
        }
    }
}
