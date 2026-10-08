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
package ai.badmonkey.agentspaces.micronaut.enroll;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Compile-time enrollment: when the application compiles with
 * {@code agentspaces-micronaut-processor}, every method carrying an
 * AgentSpaces annotation is an executable marked {@link FleetBound}, and
 * Micronaut hands each to this processor before {@code StartupEvent}. The
 * bean is instantiated and enrolled once, whatever its class-level
 * annotations, so a plain {@code @Singleton} with only a {@code @SpaceTake}
 * method joins the fleet.
 */
@Singleton
@Requires(property = "agentspaces.enabled", notEquals = "false")
public class FleetMethodProcessor implements ExecutableMethodProcessor<FleetBound> {

    private static final Logger LOG = LoggerFactory.getLogger(FleetMethodProcessor.class);

    private final BeanContext beans;
    private final FleetEnroller enroller;

    public FleetMethodProcessor(BeanContext beans, FleetEnroller enroller) {
        this.beans = Objects.requireNonNull(beans, "beans");
        this.enroller = Objects.requireNonNull(enroller, "enroller");
    }

    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        LOG.debug("processing {}#{}", beanDefinition.getName(), method.getName());
        Object bean = beans.getBean(beanDefinition);
        if (enroller.enroll(bean, beanDefinition.getName())) {
            LOG.info("enrolled {} (compile-time, via {})", beanDefinition.getName(), method.getName());
        }
    }
}
