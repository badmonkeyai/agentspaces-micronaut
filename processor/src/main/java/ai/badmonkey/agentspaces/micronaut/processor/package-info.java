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
/**
 * Compile-time support for the AgentSpaces annotations under Micronaut,
 * for the annotation-processor path only: named annotation mappers that
 * mark every {@code @SpaceTake}, {@code @SpaceNotify}, {@code @BidFunction},
 * {@code @Ballot}, {@code @OrderedTake}, and {@code @OnDecision} method as
 * an executable the library's processor receives at startup (so a bean with
 * only method annotations enrolls in the fleet), and that keep the
 * reflection the binder needs in a native image for those methods, for
 * {@code @AgentSpec} and {@code @ProvidesCapability} types, and for
 * {@code @SpaceRef} fields. Registered through
 * {@code META-INF/services/io.micronaut.inject.annotation.AnnotationMapper};
 * applications never reference these classes.
 */
package ai.badmonkey.agentspaces.micronaut.processor;
