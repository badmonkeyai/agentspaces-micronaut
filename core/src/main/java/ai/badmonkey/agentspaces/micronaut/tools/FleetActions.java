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
package ai.badmonkey.agentspaces.micronaut.tools;

import ai.badmonkey.agentspaces.agent.remote.RemoteAction;
import ai.badmonkey.agentspaces.agent.remote.RemoteActions;
import ai.badmonkey.agentspaces.micronaut.FleetContext;
import ai.badmonkey.agentspaces.micronaut.FleetParticipant;
import ai.badmonkey.agentspaces.micronaut.MicronautFleetSettings;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Order;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;

/**
 * The live catalogue of fleet tools, shared by the LangChain4j and MCP
 * bridges: the foreign cards' invocable actions ({@code RemoteActions}),
 * filtered, named, with their schemas, cached for {@code refresh-interval}
 * and diffed on that cadence, publishing {@link FleetToolsChangedEvent} when
 * the set changes. Configured under {@code agentspaces.micronaut.tools.*}.
 */
@Singleton
@Order(120)
@Requires(property = "agentspaces.enabled", notEquals = "false")
@Requires(property = "agentspaces.micronaut.tools.enabled", notEquals = "false")
public class FleetActions implements FleetParticipant {

    private static final Logger LOG = LoggerFactory.getLogger(FleetActions.class);

    private final Supplier<List<FleetAction>> actions;
    private final FleetToolFilter filter;
    private final ToolNamingStrategy naming;
    private final ToolInvocation invocation;
    private final Duration refreshInterval;
    private final int max;
    private final ApplicationEventPublisher<FleetToolsChangedEvent> events;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private List<FleetTool> snapshot = List.of();
    private Set<String> names = Set.of();
    private Instant snapshotAt = Instant.MIN;
    private ScheduledFuture<?> refresher;

    public FleetActions(FleetContext fleet, MicronautFleetSettings settings,
                        FleetToolFilter filter, ToolNamingStrategy naming,
                        ApplicationEventPublisher<FleetToolsChangedEvent> events,
                        @Named(TaskExecutors.SCHEDULED) TaskScheduler scheduler) {
        this(() -> {
            List<FleetAction> all = new ArrayList<>();
            for (RemoteAction action : RemoteActions.over(fleet.group(), fleet.peerId()).available()) {
                all.add(FleetAction.of(action));
            }
            return all;
        }, settings.getTools(), filter, naming, events, scheduler, Clock.systemUTC());
    }

    public FleetActions(Supplier<List<FleetAction>> actions, MicronautFleetSettings.Tools settings,
                        FleetToolFilter filter, ToolNamingStrategy naming,
                        ApplicationEventPublisher<FleetToolsChangedEvent> events,
                        TaskScheduler scheduler, Clock clock) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.filter = Objects.requireNonNull(filter, "filter");
        this.naming = Objects.requireNonNull(naming, "naming");
        this.invocation = new ToolInvocation(settings.getTimeout(), settings.getOnTimeout());
        this.refreshInterval = settings.getRefreshInterval();
        this.max = settings.getMax();
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = scheduler;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The invocation the bridges run tools through. */
    public ToolInvocation invocation() {
        return invocation;
    }

    /** The current tools, refreshed when the snapshot is older than the refresh interval. */
    public synchronized List<FleetTool> current() {
        if (!clock.instant().isBefore(snapshotAt.plus(refreshInterval))) {
            refresh();
        }
        return snapshot;
    }

    /** Rebuilds the set now and publishes a change event if it differs. */
    public synchronized List<FleetTool> rebuild() {
        Map<String, FleetTool> built = new LinkedHashMap<>();
        for (FleetAction action : actions.get()) {
            if (!filter.include(action)) {
                continue;
            }
            if (built.size() >= max) {
                break;
            }
            String base = naming.toolName(action);
            String name = base;
            for (int i = 2; built.containsKey(name); i++) {
                String suffix = "_" + i;
                name = (base.length() + suffix.length() > ToolNamingStrategy.MAX_LENGTH
                        ? base.substring(0, ToolNamingStrategy.MAX_LENGTH - suffix.length())
                        : base) + suffix;
            }
            built.put(name, new FleetTool(name, FleetTool.describe(action),
                    RecordSchemas.of(action.inputType()), action));
        }
        List<FleetTool> fresh = List.copyOf(built.values());
        Set<String> freshNames = new LinkedHashSet<>(built.keySet());
        Set<String> added = new LinkedHashSet<>(freshNames);
        added.removeAll(names);
        Set<String> removed = new LinkedHashSet<>(names);
        removed.removeAll(freshNames);
        snapshot = fresh;
        names = freshNames;
        snapshotAt = clock.instant();
        if (!added.isEmpty() || !removed.isEmpty()) {
            LOG.debug("fleet tools changed: +{} -{}", added, removed);
            try {
                events.publishEvent(new FleetToolsChangedEvent(added, removed, fresh));
            } catch (RuntimeException e) {
                // A listener's failure must not kill the scheduled refresh.
                LOG.warn("a fleet tools listener failed", e);
            }
        }
        return snapshot;
    }

    @Override
    public synchronized void start() {
        if (scheduler != null && refresher == null) {
            refresher = scheduler.scheduleAtFixedRate(refreshInterval, refreshInterval, this::rebuild);
        }
    }

    @Override
    public synchronized void refresh() {
        rebuild();
    }

    @Override
    public synchronized void stop() {
        if (refresher != null) {
            refresher.cancel(false);
            refresher = null;
        }
    }
}
