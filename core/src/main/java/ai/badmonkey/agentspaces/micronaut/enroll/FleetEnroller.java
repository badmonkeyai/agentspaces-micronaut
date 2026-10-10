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

import ai.badmonkey.agentspaces.agent.AgentBinder;
import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.agent.annotation.AgentSpec;
import ai.badmonkey.agentspaces.agent.annotation.BidFunction;
import ai.badmonkey.agentspaces.agent.annotation.OrderedTake;
import ai.badmonkey.agentspaces.agent.annotation.OnDecision;
import ai.badmonkey.agentspaces.agent.annotation.Ballot;
import ai.badmonkey.agentspaces.agent.annotation.OnEstimate;
import ai.badmonkey.agentspaces.agent.annotation.Part;
import ai.badmonkey.agentspaces.agent.annotation.Propose;
import ai.badmonkey.agentspaces.agent.annotation.ProvidesCapability;
import ai.badmonkey.agentspaces.agent.annotation.SpaceJoin;
import ai.badmonkey.agentspaces.agent.annotation.SpaceNotify;
import ai.badmonkey.agentspaces.agent.annotation.SpaceReduce;
import ai.badmonkey.agentspaces.agent.annotation.SpaceRef;
import ai.badmonkey.agentspaces.agent.annotation.SpaceTake;
import ai.badmonkey.agentspaces.micronaut.MicronautFleetSettings;
import ai.badmonkey.agentspaces.spring.AgentSpacesProperties;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Enrolls annotated beans in the fleet (spec §10.3, §10.4, §10.5), the
 * Micronaut counterpart of the Spring starter's bean post-processor. Micronaut
 * singletons are lazy, so rather than waiting for beans to be created the
 * enroller finds every bean definition carrying {@link AgentSpec} (directly,
 * or through a stereotype such as {@code @SpaceAgent}) or
 * {@link ProvidesCapability}, instantiates it, and binds it through
 * {@link AgentSpaces#bind(Object)}: worker loops start, bids wire, and the
 * AgentCard publishes into every joined group whose spaces satisfy the
 * bean's annotations. A bean referencing spaces no group covers fails at
 * startup naming the bean, the spaces, and the configured groups.
 *
 * <p>Beans whose only fleet annotations are on methods enroll through
 * {@link #enroll(Object, String)}, which the annotation-processor module's
 * executable-method processor calls, or by carrying {@code @SpaceAgent}.
 * The 0.3.0 method annotations ({@code @SpaceJoin}, {@code @Propose},
 * {@code @OnEstimate}, {@code @SpaceReduce}) enroll the same way.
 *
 * <p>An agent bean must be free of Micronaut AOP: an interceptor binding
 * ({@code @Retryable}, {@code @Cacheable}, {@code @Transactional}, ...)
 * generates a subclass whose overriding methods carry none of the fleet
 * annotations, and the binder reads the runtime class. Such a bean fails at
 * startup with a message saying so, rather than silently not enrolling.
 */
@Singleton
@Requires(property = "agentspaces.enabled", notEquals = "false")
public class FleetEnroller {

    private static final Logger LOG = LoggerFactory.getLogger(FleetEnroller.class);

    private final BeanContext beans;
    private final AgentSpaces spaces;
    private final List<String> groupOrder;
    private final boolean enabled;
    private final Set<Object> enrolled = Collections.newSetFromMap(new IdentityHashMap<>());

    public FleetEnroller(BeanContext beans, AgentSpaces spaces, AgentSpacesProperties properties,
                         MicronautFleetSettings settings) {
        this.beans = Objects.requireNonNull(beans, "beans");
        this.spaces = Objects.requireNonNull(spaces, "spaces");
        List<String> order = new ArrayList<>();
        properties.getGroups().forEach(group -> order.add(group.getName()));
        this.groupOrder = List.copyOf(order);
        this.enabled = settings.getEnroll().isEnabled();
    }

    /** Finds and binds every annotated bean not yet enrolled. */
    public synchronized void enroll() {
        if (!enabled) {
            return;
        }
        Set<BeanDefinition<?>> definitions = new LinkedHashSet<>();
        definitions.addAll(beans.getBeanDefinitions(Qualifiers.byStereotype(AgentSpec.class)));
        definitions.addAll(beans.getBeanDefinitions(Qualifiers.byStereotype(ProvidesCapability.class)));
        for (BeanDefinition<?> definition : definitions) {
            Object bean = beans.getBean(definition);
            enroll(bean, definition.getName());
        }
    }

    /**
     * Binds one bean if it carries fleet annotations and is not yet bound.
     *
     * @param bean     the bean
     * @param beanName its name, for failure messages
     * @return whether the bean was bound by this call
     */
    public synchronized boolean enroll(Object bean, String beanName) {
        Objects.requireNonNull(bean, "bean");
        if (!enabled || enrolled.contains(bean)) {
            return false;
        }
        Class<?> type = bean.getClass();
        // Micronaut AOP subclasses carry none of the user class's annotations;
        // decide on the user class, then refuse the proxy with the reason.
        Class<?> user = userClass(type);
        // The core binder's declaredBindings covers every method annotation, the
        // 0.3.0 set included (its @SpaceReduce branch landed 2026-10-09).
        boolean coreBinds = AgentBinder.isAgentType(user) || AgentBinder.hasBindings(user);
        boolean enrolls = coreBinds || user.isAnnotationPresent(ProvidesCapability.class);
        if (!enrolls) {
            return false;
        }
        if (user != type) {
            throw new IllegalStateException("bean '" + beanName + "' carries fleet annotations but"
                    + " is intercepted by Micronaut AOP (" + type.getName() + "): an interceptor"
                    + " binding such as @Retryable, @Cacheable, @Transactional, or a custom @Around"
                    + " generates a subclass that hides the annotations from the fleet binder."
                    + " Keep the agent bean free of interceptor annotations and put them on a"
                    + " separate bean (a controller that delegates to the agent, for instance).");
        }
        try {
            spaces.bind(bean);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Set<String> referenced = referencedSpaces(type);
            if (referenced.isEmpty()) {
                throw new IllegalStateException("bean '" + beanName + "' could not be enrolled: "
                        + e.getMessage(), e);
            }
            throw new IllegalStateException("bean '" + beanName + "' references spaces "
                    + referenced + " but no configured group registers them all"
                    + " (" + e.getMessage() + "); groups: " + groupOrder, e);
        }
        enrolled.add(bean);
        LOG.debug("enrolled {} in the fleet", beanName);
        return true;
    }

    /** @return the beans enrolled so far */
    public synchronized List<Object> enrolled() {
        return List.copyOf(enrolled);
    }


    /**
     * The space names a bean's annotations reference; inferred (empty) names
     * excluded. Covers the 0.3.0 set: {@code @SpaceJoin} ({@code space},
     * {@code resultSpace}, each {@code @Part} space), {@code @Propose}
     * ({@code space}, {@code vote}), {@code @OnEstimate}
     * ({@code resultSpace}), and {@code @SpaceReduce} ({@code space}).
     */
    static Set<String> referencedSpaces(Class<?> type) {
        Set<String> referenced = new LinkedHashSet<>();
        for (Method method : type.getMethods()) {
            SpaceTake take = method.getAnnotation(SpaceTake.class);
            if (take != null) {
                referenced.add(take.space());
                referenced.add(take.resultSpace());
            }
            SpaceNotify notify = method.getAnnotation(SpaceNotify.class);
            if (notify != null) {
                referenced.add(notify.space());
                referenced.add(notify.resultSpace());
            }
            BidFunction bid = method.getAnnotation(BidFunction.class);
            if (bid != null) {
                referenced.add(bid.space());
            }
            Ballot ballot = method.getAnnotation(Ballot.class);
            if (ballot != null) {
                referenced.add(ballot.space());
            }
            OnDecision decision = method.getAnnotation(OnDecision.class);
            if (decision != null) {
                referenced.add(decision.space());
                referenced.add(decision.resultSpace());
            }
            OrderedTake ordered = method.getAnnotation(OrderedTake.class);
            if (ordered != null) {
                referenced.add(ordered.space());
                referenced.add(ordered.resultSpace());
            }
            SpaceJoin join = method.getAnnotation(SpaceJoin.class);
            if (join != null) {
                referenced.add(join.space());
                referenced.add(join.resultSpace());
                for (Part part : join.parts()) {
                    referenced.add(part.space());
                }
            }
            Propose propose = method.getAnnotation(Propose.class);
            if (propose != null) {
                referenced.add(propose.space());
                referenced.add(propose.vote());
            }
            OnEstimate estimate = method.getAnnotation(OnEstimate.class);
            if (estimate != null) {
                referenced.add(estimate.resultSpace());
            }
            SpaceReduce reduce = method.getAnnotation(SpaceReduce.class);
            if (reduce != null) {
                referenced.add(reduce.space());
            }
        }
        for (Class<?> at = type; at != null && at != Object.class; at = at.getSuperclass()) {
            for (Field field : at.getDeclaredFields()) {
                SpaceRef ref = field.getAnnotation(SpaceRef.class);
                if (ref != null) {
                    referenced.add(ref.value());
                }
            }
        }
        referenced.remove("");
        return referenced;
    }

    /** The application's class behind a Micronaut AOP subclass (one implementing {@code io.micronaut.aop.Intercepted}), or the class itself. */
    static Class<?> userClass(Class<?> type) {
        Class<?> at = type;
        while (at != null && at != Object.class && intercepted(at)) {
            at = at.getSuperclass();
        }
        return at == null || at == Object.class ? type : at;
    }

    private static boolean intercepted(Class<?> type) {
        for (Class<?> implemented : type.getInterfaces()) {
            if ("io.micronaut.aop.Intercepted".equals(implemented.getName())) {
                return true;
            }
        }
        return false;
    }
}
