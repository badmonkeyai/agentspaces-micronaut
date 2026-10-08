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
package ai.badmonkey.agentspaces.micronaut.processor;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps the AgentSpaces type annotations ({@code @AgentSpec},
 * {@code @ProvidesCapability}) to {@code @Introspected} and
 * {@code @ReflectiveAccess}, so the binder's reflection over the class and
 * its {@code @SpaceRef} fields survives a native image.
 */
public abstract class FleetTypeMapper implements NamedAnnotationMapper {

    private final String name;

    FleetTypeMapper(String simpleName) {
        this.name = "ai.badmonkey.agentspaces.agent.annotation." + simpleName;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation,
                                              VisitorContext visitorContext) {
        return List.of(
                AnnotationValue.builder(Introspected.class).build(),
                AnnotationValue.builder(ReflectiveAccess.class).build());
    }

    /** {@code @AgentSpec}. */
    public static final class AgentSpecMapper extends FleetTypeMapper {
        public AgentSpecMapper() {
            super("AgentSpec");
        }
    }

    /** {@code @ProvidesCapability}. */
    public static final class ProvidesCapabilityMapper extends FleetTypeMapper {
        public ProvidesCapabilityMapper() {
            super("ProvidesCapability");
        }
    }

    /** {@code @SpaceRef}, a field the binder sets reflectively. */
    public static final class SpaceRefMapper implements NamedAnnotationMapper {
        @Override
        public String getName() {
            return "ai.badmonkey.agentspaces.agent.annotation.SpaceRef";
        }

        @Override
        public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation,
                                            VisitorContext visitorContext) {
            return List.of(AnnotationValue.builder(ReflectiveAccess.class).build());
        }
    }
}
