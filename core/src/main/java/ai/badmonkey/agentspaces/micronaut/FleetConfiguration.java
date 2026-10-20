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
        return bind(tree);
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
     * {@code social} per group. The Spring starter ignores them because Boot
     * ignores unknown keys; the runtime's binding rejects unknown keys, so
     * they are dropped here before the conversion.
     */
    static final Set<String> FOREIGN_BLOCKS = Set.of("bootstrap", "beacon", "social");

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
