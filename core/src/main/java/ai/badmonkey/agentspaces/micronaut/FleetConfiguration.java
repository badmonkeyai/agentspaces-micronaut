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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        Objects.requireNonNull(tree, "tree");
        @SuppressWarnings("unchecked")
        Map<String, Object> cleaned = (Map<String, Object>) withoutIndexedKeys(tree);
        // This project's own keys, not the Spring starter's.
        cleaned.remove("enabled");
        cleaned.remove("micronaut");
        return mapper().convertValue(cleaned, AgentSpacesProperties.class);
    }

    private static ObjectMapper mapper() {
        SimpleModule durations = new SimpleModule("agentspaces-durations")
                .addDeserializer(Duration.class, new LenientDuration());
        return new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .registerModule(durations);
    }

    @SuppressWarnings("unchecked")
    private static Object withoutIndexedKeys(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (key.contains("[")) {
                    continue;
                }
                copy.put(key, withoutIndexedKeys(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(withoutIndexedKeys(item));
            }
            return copy;
        }
        return value;
    }

    /** ISO-8601, a plain number of milliseconds, or Spring's {@code 5m}-style suffix. */
    static final class LenientDuration extends JsonDeserializer<Duration> {
        @Override
        public Duration deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            if (parser.currentToken().isNumeric()) {
                return Duration.ofMillis(parser.getLongValue());
            }
            return parse(parser.getText());
        }

        static Duration parse(String text) {
            String value = text.trim();
            if (value.isEmpty()) {
                return null;
            }
            String upper = value.toUpperCase();
            if (upper.startsWith("P") || upper.startsWith("-P")) {
                return Duration.parse(upper);
            }
            if (value.chars().allMatch(Character::isDigit)) {
                return Duration.ofMillis(Long.parseLong(value));
            }
            int split = value.length();
            while (split > 0 && !Character.isDigit(value.charAt(split - 1))) {
                split--;
            }
            long amount = Long.parseLong(value.substring(0, split).trim());
            return switch (value.substring(split).trim().toLowerCase()) {
                case "ns" -> Duration.ofNanos(amount);
                case "us" -> Duration.ofNanos(amount * 1_000);
                case "ms" -> Duration.ofMillis(amount);
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                case "d" -> Duration.ofDays(amount);
                default -> throw new IllegalArgumentException("not a duration: '" + text + "'");
            };
        }
    }
}
