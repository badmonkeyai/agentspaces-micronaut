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
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps one AgentSpaces method annotation, by name, to {@code @FleetBound}
 * (itself {@code @Executable(processOnStartup = true)}, so the method is an
 * executable the library's processor receives at startup) and
 * {@code @ReflectiveAccess} (so a native image keeps the reflection the
 * binder needs). Named, so the AgentSpaces jars need not be on the
 * annotation-processor path.
 */
public abstract class FleetMethodMapper implements NamedAnnotationMapper {

    static final String FLEET_BOUND = "ai.badmonkey.agentspaces.micronaut.enroll.FleetBound";
    private static final String PACKAGE = "ai.badmonkey.agentspaces.agent.annotation.";

    private final String name;

    FleetMethodMapper(String simpleName) {
        this.name = PACKAGE + simpleName;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation,
                                              VisitorContext visitorContext) {
        return List.of(
                AnnotationValue.builder(ReflectiveAccess.class).build(),
                AnnotationValue.builder(FLEET_BOUND).build());
    }

    /** {@code @SpaceTake}. */
    public static final class SpaceTakeMapper extends FleetMethodMapper {
        public SpaceTakeMapper() {
            super("SpaceTake");
        }
    }

    /** {@code @SpaceNotify}. */
    public static final class SpaceNotifyMapper extends FleetMethodMapper {
        public SpaceNotifyMapper() {
            super("SpaceNotify");
        }
    }

    /** {@code @BidFunction}. */
    public static final class BidFunctionMapper extends FleetMethodMapper {
        public BidFunctionMapper() {
            super("BidFunction");
        }
    }

    /** {@code @Ballot}. */
    public static final class BallotMapper extends FleetMethodMapper {
        public BallotMapper() {
            super("Ballot");
        }
    }

    /** {@code @OrderedTake}. */
    public static final class OrderedTakeMapper extends FleetMethodMapper {
        public OrderedTakeMapper() {
            super("OrderedTake");
        }
    }

    /** {@code @OnDecision}. */
    public static final class OnDecisionMapper extends FleetMethodMapper {
        public OnDecisionMapper() {
            super("OnDecision");
        }
    }

    /** {@code @SpaceJoin} (0.3.0). */
    public static final class SpaceJoinMapper extends FleetMethodMapper {
        public SpaceJoinMapper() {
            super("SpaceJoin");
        }
    }

    /** {@code @Propose} (0.3.0). */
    public static final class ProposeMapper extends FleetMethodMapper {
        public ProposeMapper() {
            super("Propose");
        }
    }

    /** {@code @OnEstimate} (0.3.0). */
    public static final class OnEstimateMapper extends FleetMethodMapper {
        public OnEstimateMapper() {
            super("OnEstimate");
        }
    }

    /** {@code @SpaceReduce} (0.3.0). */
    public static final class SpaceReduceMapper extends FleetMethodMapper {
        public SpaceReduceMapper() {
            super("SpaceReduce");
        }
    }
}
