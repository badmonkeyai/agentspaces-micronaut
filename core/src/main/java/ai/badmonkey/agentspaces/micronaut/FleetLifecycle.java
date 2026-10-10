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

import ai.badmonkey.agentspaces.agent.AgentSpaces;
import ai.badmonkey.agentspaces.micronaut.enroll.FleetEnroller;
import ai.badmonkey.agentspaces.peering.node.PeerNode;
import ai.badmonkey.agentspaces.spring.AgentSpacesProperties;
import ai.badmonkey.agentspaces.spring.Authorizers;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

/**
 * Drives the fabric with the application's lifecycle, as the Spring starter's
 * {@code AgentSpacesLifecycle} does: on {@link StartupEvent}, enroll the
 * annotated beans, start the node ticking (membership probes, anti-entropy,
 * self-advertisement refresh), start every {@link FleetParticipant}, and
 * schedule the card refresh (bound agents' cards, each participant's
 * {@code refresh()}, and the OIDC token-file re-read) on Micronaut's scheduled
 * executor; on shutdown, stop all of it in reverse and close the facade and
 * the node.
 */
@Singleton
@Requires(property = "agentspaces.enabled", notEquals = "false")
public class FleetLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(FleetLifecycle.class);

    private final PeerNode node;
    private final AgentSpaces spaces;
    private final Authorizers authorizers;
    private final FleetEnroller enroller;
    private final List<ai.badmonkey.agentspaces.runtime.FleetParticipant> participants;
    private final TaskScheduler scheduler;
    private final Duration tickPeriod;
    private final Duration cardRefreshPeriod;
    private ScheduledFuture<?> refresher;
    private volatile boolean running;

    public FleetLifecycle(AgentSpacesProperties properties, PeerNode node, AgentSpaces spaces,
                          Authorizers authorizers, FleetEnroller enroller,
                          List<ai.badmonkey.agentspaces.runtime.FleetParticipant> participants,
                          @Named(TaskExecutors.SCHEDULED) TaskScheduler scheduler) {
        this.node = Objects.requireNonNull(node, "node");
        this.spaces = Objects.requireNonNull(spaces, "spaces");
        this.authorizers = Objects.requireNonNull(authorizers, "authorizers");
        this.enroller = Objects.requireNonNull(enroller, "enroller");
        this.participants = List.copyOf(participants);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.tickPeriod = Duration.ofMillis(properties.getTickMillis());
        this.cardRefreshPeriod = Duration.ofMillis(properties.getCardRefreshMillis());
    }

    @EventListener
    public void onStartup(StartupEvent event) {
        start();
    }

    @EventListener
    public void onShutdown(ShutdownEvent event) {
        stop();
    }

    /** Enrolls, ticks, starts participants, and schedules the refresh; idempotent. */
    public synchronized void start() {
        if (running) {
            return;
        }
        enroller.enroll();
        node.startTicking(tickPeriod);
        for (ai.badmonkey.agentspaces.runtime.FleetParticipant participant : participants) {
            participant.start();
        }
        refresher = scheduler.scheduleAtFixedRate(cardRefreshPeriod, cardRefreshPeriod, this::refresh);
        running = true;
        LOG.info("AgentSpaces peer {} running; groups {}", node.identity().peerId(), spaces.groupNames());
    }

    /** One refresh tick: cards, participants, then the token hook; none may kill the schedule. */
    void refresh() {
        try {
            spaces.refreshCards();
        } catch (RuntimeException e) {
            LOG.warn("card refresh failed", e);
        }
        for (ai.badmonkey.agentspaces.runtime.FleetParticipant participant : participants) {
            try {
                participant.refresh();
            } catch (RuntimeException e) {
                LOG.warn("refresh of {} failed", participant, e);
            }
        }
        try {
            authorizers.refreshToken();
        } catch (RuntimeException e) {
            LOG.warn("token refresh failed", e);
        }
    }

    @PreDestroy
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (refresher != null) {
            refresher.cancel(false);
            refresher = null;
        }
        List<ai.badmonkey.agentspaces.runtime.FleetParticipant> reversed = new ArrayList<>(participants);
        Collections.reverse(reversed);
        for (ai.badmonkey.agentspaces.runtime.FleetParticipant participant : reversed) {
            try {
                participant.stop();
            } catch (RuntimeException e) {
                LOG.warn("stop of {} failed", participant, e);
            }
        }
        spaces.close();
        node.close();
    }

    public boolean isRunning() {
        return running;
    }
}
