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

import ai.badmonkey.agentspaces.spring.AgentSpacesProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import ai.badmonkey.agentspaces.runtime.PropertiesLoader;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Binds the {@code agentspaces.*} subtree of the Micronaut environment into
 * the Spring starter's {@link AgentSpacesProperties}, so one YAML fragment
 * configures a peer under either framework. Micronaut can only bind
 * configuration into classes it compiled, and the POJO lives in the published
 * autoconfigure jar, so the binding is a Jackson conversion of the nested map
 * the environment already holds: kebab-case keys to the POJO's camel-case
 * setters, lists of maps to the nested group and space definitions, and
 * durations in either ISO-8601 ({@code PT5M}) or Spring's short form
 * ({@code 5m}, {@code 250ms}, {@code 2h}, {@code 1d}).
 */
@Factory
@Requires(property = "agentspaces.enabled", notEquals = "false")
public class FleetConfiguration {

    /**
     * The bound properties, as the Spring starter would see them.
     *
     * @param environment the Micronaut environment
     * @return the properties
     */
    @Singleton
    @Requires(missingBeans = AgentSpacesProperties.class)
    public AgentSpacesProperties agentSpacesProperties(Environment environment) {
        @SuppressWarnings("unchecked")
        Map<String, Object> tree = environment.getProperty("agentspaces", Map.class)
                .map(m -> (Map<String, Object>) m).orElse(Map.of());
        return bind(withoutEnvironmentVariants(tree, System.getenv()));
    }

    /**
     * Micronaut expands an environment variable such as {@code AGENTSPACES_FLOWS_RECONCILE} into every
     * dotted-and-hyphenated spelling ({@code flows.reconcile}, {@code flows-reconcile}), and only one of them is
     * a property the runtime knows; the others would be refused as unknown keys. For each {@code AGENTSPACES_*}
     * variable this tries each spelling the tree carries on its own and drops the ones the binding refuses, so a
     * deployer that hands a package its join and its switches as environment (the Bad Monkey console does) starts
     * the peer. A key the environment did not produce is never dropped, so a typo in a file still surfaces.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> withoutEnvironmentVariants(Map<String, Object> tree, Map<String, String> env) {
        Map<String, Object> copy = deepCopy(tree);
        for (String name : env.keySet()) {
            if (!name.startsWith("AGENTSPACES_") || name.length() <= "AGENTSPACES_".length()) {
                continue;
            }
            // one segment too: AGENTSPACES_SEEDS lands as a top-level `seeds` the runtime binds under a group, never here
            String[] segments = name.substring("AGENTSPACES_".length()).toLowerCase(java.util.Locale.ROOT).split("_");
            for (List<String> path : spellings(segments)) {
                Object value = valueAt(copy, path);
                if (value == null) {
                    continue;
                }
                try {
                    PropertiesLoader.map(withoutForeignBlocks(treeOf(path, value)));
                } catch (RuntimeException refused) {
                    remove(copy, path);
                }
            }
        }
        return copy;
    }

    /** Every way to spell the segments as a path: each gap joins with a hyphen (one key) or a dot (a nested key). */
    static List<List<String>> spellings(String[] segments) {
        List<List<String>> out = new ArrayList<>();
        int gaps = segments.length - 1;
        for (int mask = 0; mask < (1 << gaps); mask++) {
            List<String> path = new ArrayList<>();
            StringBuilder current = new StringBuilder(segments[0]);
            for (int i = 0; i < gaps; i++) {
                if ((mask & (1 << i)) != 0) {
                    current.append('-').append(segments[i + 1]);
                } else {
                    path.add(current.toString());
                    current = new StringBuilder(segments[i + 1]);
                }
            }
            path.add(current.toString());
            out.add(path);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object valueAt(Map<String, Object> tree, List<String> path) {
        Object node = tree;
        for (String key : path) {
            if (!(node instanceof Map<?, ?> map) || !map.containsKey(key)) {
                return null;
            }
            node = ((Map<String, Object>) map).get(key);
        }
        return node;
    }

    private static Map<String, Object> treeOf(List<String> path, Object value) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> current = root;
        for (int i = 0; i < path.size() - 1; i++) {
            Map<String, Object> next = new LinkedHashMap<>();
            current.put(path.get(i), next);
            current = next;
        }
        current.put(path.get(path.size() - 1), value);
        return root;
    }

    @SuppressWarnings("unchecked")
    private static void remove(Map<String, Object> tree, List<String> path) {
        Map<String, Object> current = tree;
        for (int i = 0; i < path.size() - 1; i++) {
            Object next = current.get(path.get(i));
            if (!(next instanceof Map<?, ?>)) {
                return;
            }
            current = (Map<String, Object>) next;
        }
        current.remove(path.get(path.size() - 1));
        // an emptied nested block goes too, so a block the environment alone created leaves no trace
        if (path.size() > 1 && current.isEmpty()) {
            remove(tree, path.subList(0, path.size() - 1));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> tree) {
        Map<String, Object> copy = new LinkedHashMap<>();
        tree.forEach((k, v) -> copy.put(k, v instanceof Map<?, ?> m ? deepCopy((Map<String, Object>) m) : v));
        return copy;
    }

    /**
     * Binds a nested map (kebab-case keys, lists for repeated sections) into
     * the properties POJO. Indexed keys ({@code groups[0]}) that Micronaut
     * keeps beside the list form are dropped, since the list carries them.
     *
     * @param tree the {@code agentspaces} subtree
     * @return the bound properties
     */
    public static AgentSpacesProperties bind(Map<String, Object> tree) {
        // The runtime's binding: the same conversion, in the artifact every host shares.
        return PropertiesLoader.map(withoutForeignBlocks(tree));
    }

    /**
     * The blocks of the {@code agentspaces} tree that other root projects bind
     * (SRAD discovery-bootstrap-customization §6.5): {@code bootstrap},
     * {@code beacon}, and {@code social} fleet-wide, {@code bootstrap} and
     * {@code social} per group, and {@code pdt}, which this project binds
     * itself in {@link ai.badmonkey.agentspaces.micronaut.pdt.PdtSettings}
     * (srad/agentspaces-pdt.md PD-C-03). The Spring starter ignores them because
     * Boot ignores unknown keys; the runtime's binding rejects unknown keys, so
     * they are dropped here before the conversion.
     */
    static final Set<String> FOREIGN_BLOCKS = Set.of("bootstrap", "beacon", "social", "pdt");

    static final Set<String> FOREIGN_GROUP_BLOCKS = Set.of("bootstrap", "social");

    @SuppressWarnings("unchecked")
    static Map<String, Object> withoutForeignBlocks(Map<String, Object> tree) {
        Map<String, Object> copy = withoutBlocks(tree, FOREIGN_BLOCKS);
        if (copy.get("groups") instanceof List<?> groups) {
            List<Object> cleaned = new ArrayList<>(groups.size());
            for (Object group : groups) {
                cleaned.add(group instanceof Map<?, ?> map
                        ? withoutBlocks((Map<String, Object>) map, FOREIGN_GROUP_BLOCKS) : group);
            }
            copy.put("groups", cleaned);
        }
        return copy;
    }

    /**
     * Drops the named blocks, whether nested ({@code bootstrap: {...}}) or
     * flattened as dotted keys ({@code bootstrap.dns-srv}), which is how the
     * environment presents a block inside a list element.
     */
    private static Map<String, Object> withoutBlocks(Map<String, Object> map, Set<String> blocks) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            int dot = key.indexOf('.');
            String head = dot < 0 ? key : key.substring(0, dot);
            if (!blocks.contains(head)) {
                copy.put(key, entry.getValue());
            }
        }
        return copy;
    }
}
