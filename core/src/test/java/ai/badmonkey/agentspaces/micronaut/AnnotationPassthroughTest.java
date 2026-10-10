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
import ai.badmonkey.agentspaces.agent.reduce.Reductions;
import ai.badmonkey.agentspaces.api.ad.CapabilityAdvertisement;
import ai.badmonkey.agentspaces.api.space.Lease;
import ai.badmonkey.agentspaces.api.space.Space;
import ai.badmonkey.agentspaces.api.space.Template;
import ai.badmonkey.agentspaces.capabilities.vote.VoteCapability;
import ai.badmonkey.agentspaces.identity.FileKeystore;
import ai.badmonkey.agentspaces.identity.PeerIdentity;
import ai.badmonkey.agentspaces.micronaut.enroll.FleetEnroller;
import ai.badmonkey.agentspaces.micronaut.support.Bidder;
import ai.badmonkey.agentspaces.micronaut.support.Contexts;
import ai.badmonkey.agentspaces.micronaut.support.Forker;
import ai.badmonkey.agentspaces.micronaut.support.Gauge;
import ai.badmonkey.agentspaces.micronaut.support.ItCapability;
import ai.badmonkey.agentspaces.micronaut.support.ItFinding;
import ai.badmonkey.agentspaces.micronaut.support.ItTally;
import ai.badmonkey.agentspaces.micronaut.support.ItTask;
import ai.badmonkey.agentspaces.micronaut.support.LeasedJoiner;
import ai.badmonkey.agentspaces.micronaut.support.Mover;
import ai.badmonkey.agentspaces.micronaut.support.OrderedClerk;
import ai.badmonkey.agentspaces.micronaut.support.Panel;
import ai.badmonkey.agentspaces.micronaut.support.TagFolder;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every binding annotation and return convention the README lists as bound,
 * proven through Micronaut-managed beans with each outcome read back from a
 * space: {@code @SpaceNotify} with an {@code Entries} fork of a {@code Tagged}
 * entry and a {@code @SpaceRef} write; {@code @BidFunction} on an AUCTION
 * space; {@code @Propose}, {@code @Ballot}, {@code @OnDecision}, and a
 * {@code @CapabilityRef} client in one bean; a {@code Motion} return; a
 * {@code Contribution} return settled by {@code @OnEstimate};
 * {@code @SpaceJoin} in {@code LEASED} mode over a {@code keyTag} part;
 * {@code @SpaceReduce} with {@code keyTag} and {@code accumulatorLease};
 * {@code @OrderedTake} over a two-peer ordered log; and a
 * {@code @ProvidesCapability} bean advertised into the group.
 */
class AnnotationPassthroughTest {

    private static final Lease TEN_MINUTES = Lease.of(Duration.ofMinutes(10));

    private static Space space(ApplicationContext ctx, String name) {
        return ctx.getBean(AgentSpaces.class).group("it-fleet").space(name);
    }

    @Test
    @Timeout(60)
    void aNotifyForksATaggedEntryAndATallyAndWritesThroughItsSpaceRef() {
        try (ApplicationContext ctx = Contexts.run(Contexts.passthrough(Contexts.freePort()),
                Map.of("test.app", "forker"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled()).singleElement().isInstanceOf(Forker.class);
            Space tasks = space(ctx, "tasks");
            tasks.write(new ItTask("fork", 3), TEN_MINUTES);

            assertThat(Contexts.await(Duration.ofSeconds(20), () ->
                    !tasks.readAll(Template.of(ItTally.class), 10).isEmpty())).isTrue();
            List<Space.Entry<ItFinding>> findings = tasks.readAllEntries(Template.of(ItFinding.class), 10);
            assertThat(findings).singleElement().satisfies(entry -> {
                assertThat(entry.value()).isEqualTo(new ItFinding("fork", "forked"));
                assertThat(entry.tags()).containsEntry("kind", "fork");
            });
            assertThat(tasks.readAll(Template.of(ItTally.class), 10)).containsExactly(new ItTally("fork", 3));
            assertThat(tasks.readAll(Template.of(ItTask.class), 10)).as("a notify never consumes its cue")
                    .containsExactly(new ItTask("fork", 3));
            assertThat(space(ctx, "data").read(Template.of(ItFinding.class), Duration.ofSeconds(10)))
                    .hasValue(new ItFinding("ref:fork", "via @SpaceRef"));
        }
    }

    @Test
    @Timeout(60)
    void aBidFunctionPricesTheAuctionSpaceTheTakeWins() {
        try (ApplicationContext ctx = Contexts.run(Contexts.passthrough(Contexts.freePort()),
                Map.of("test.app", "bidder"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled()).singleElement().isInstanceOf(Bidder.class);
            space(ctx, "bids").write(new ItTask("auction", 2), TEN_MINUTES);

            assertThat(space(ctx, "findings").read(Template.of(ItFinding.class), Duration.ofSeconds(20)))
                    .hasValue(new ItFinding("auction", "won at bid 3.0"));
            assertThat(Contexts.await(Duration.ofSeconds(10), () ->
                    space(ctx, "bids").readAll(Template.of(ItTask.class), 10).isEmpty())).isTrue();
        }
    }

    @Test
    @Timeout(60)
    void aProposeOpensAVoteTheBallotCastsAndTheDecisionLandsWithTheClientsTally() {
        try (ApplicationContext ctx = Contexts.run(Contexts.passthrough(Contexts.freePort()),
                Map.of("test.app", "panel"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled()).singleElement().isInstanceOf(Panel.class);
            Space votes = space(ctx, "votes");
            space(ctx, "tasks").write(new ItTask("merge", 1), TEN_MINUTES);

            assertThat(Contexts.await(Duration.ofSeconds(20), () ->
                    !votes.readAll(Template.of(VoteCapability.Proposal.class), 10).isEmpty())).isTrue();
            assertThat(votes.readAll(Template.of(VoteCapability.Proposal.class), 10)).singleElement()
                    .satisfies(proposal -> {
                        assertThat(proposal.proposalId()).isEqualTo("task:merge");
                        assertThat(proposal.question()).isEqualTo("Approve merge?");
                        assertThat(proposal.options()).containsExactly("approve", "deny");
                        assertThat(proposal.quorum()).isEqualTo(1);
                    });
            assertThat(Contexts.await(Duration.ofSeconds(20), () ->
                    !votes.readAll(Template.of(VoteCapability.Ballot.class), 10).isEmpty())).isTrue();
            assertThat(votes.readAll(Template.of(VoteCapability.Ballot.class), 10)).singleElement()
                    .satisfies(ballot -> {
                        assertThat(ballot.proposalId()).isEqualTo("task:merge");
                        assertThat(ballot.option()).isEqualTo("approve");
                    });
            assertThat(space(ctx, "findings").read(Template.of(ItFinding.class), Duration.ofSeconds(20)))
                    .hasValue(new ItFinding("task:merge", "decided: approve tally {approve=1, deny=0}"));
        }
    }

    @Test
    @Timeout(60)
    void aMotionReturnOpensAVoteAndWritesNoEntry() {
        try (ApplicationContext ctx = Contexts.run(Contexts.passthrough(Contexts.freePort()),
                Map.of("test.app", "mover"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled()).singleElement().isInstanceOf(Mover.class);
            Space votes = space(ctx, "votes");
            space(ctx, "tasks").write(new ItTask("relocate", 1), TEN_MINUTES);

            assertThat(Contexts.await(Duration.ofSeconds(20), () ->
                    !votes.readAll(Template.of(VoteCapability.Proposal.class), 10).isEmpty())).isTrue();
            assertThat(votes.readAll(Template.of(VoteCapability.Proposal.class), 10)).singleElement()
                    .satisfies(proposal -> {
                        assertThat(proposal.proposalId()).isEqualTo("motion:relocate");
                        assertThat(proposal.question()).isEqualTo("Move relocate?");
                        assertThat(proposal.options()).containsExactly("yes", "no");
                    });
            assertThat(space(ctx, "tasks").readAll(Template.of(ItFinding.class), 10)).isEmpty();
        }
    }

    @Test
    @Timeout(60)
    void aContributionFeedsTheAggregateAndOnEstimateReportsWhenItSettles() {
        try (ApplicationContext ctx = Contexts.run(Contexts.passthrough(Contexts.freePort()),
                Map.of("test.app", "gauge"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled()).singleElement().isInstanceOf(Gauge.class);
            space(ctx, "tasks").write(new ItTask("cpu", 7), TEN_MINUTES);

            assertThat(space(ctx, "findings").read(Template.of(ItFinding.class), Duration.ofSeconds(30)))
                    .hasValue(new ItFinding("load:cpu", "estimate: 7"));
        }
    }

    @Test
    @Timeout(60)
    void aLeasedJoinFiresFromItsTicketOverAFieldKeyedAndATagKeyedPart() {
        try (ApplicationContext ctx = Contexts.run(Contexts.passthrough(Contexts.freePort()),
                Map.of("test.app", "leased-joiner"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled()).singleElement().isInstanceOf(LeasedJoiner.class);
            Space tasks = space(ctx, "tasks");
            tasks.write(new ItTask("pair", 4), TEN_MINUTES);
            tasks.write(new ItFinding("elsewhere", "tagged part"), TEN_MINUTES, Map.of("topic", "pair"));

            assertThat(space(ctx, "data").read(Template.of(ItTally.class), Duration.ofSeconds(20)))
                    .hasValue(new ItTally("pair", 4));
        }
    }

    @Test
    @Timeout(60)
    void aTagKeyedReduceFoldsPerTagAndItsAccumulatorLapsesWhenIdle() {
        try (ApplicationContext ctx = Contexts.run(Contexts.passthrough(Contexts.freePort()),
                Map.of("test.app", "tag-folder"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled()).singleElement().isInstanceOf(TagFolder.class);
            Space tasks = space(ctx, "tasks");
            tasks.write(new ItTask("field-ignored", 1), TEN_MINUTES, Map.of("topic", "fold"));
            tasks.write(new ItTask("field-ignored", 2), TEN_MINUTES, Map.of("topic", "fold"));

            assertThat(Contexts.await(Duration.ofSeconds(20), () -> current(tasks)
                    .map(entry -> entry.value().points() == 3).orElse(false))).isTrue();
            Space.Entry<ItTally> folded = current(tasks).orElseThrow();
            assertThat(folded.tags()).containsEntry(Reductions.KEY_TAG, "fold")
                    .containsEntry(Reductions.REDUCE_TAG, "tagfold");
            assertThat(Reductions.steps(folded)).isEqualTo(2);

            assertThat(Contexts.await(Duration.ofSeconds(20), () -> current(tasks).isEmpty()))
                    .as("the accumulator lapses after its 2s idle lease").isTrue();
            tasks.write(new ItTask("field-ignored", 5), TEN_MINUTES, Map.of("topic", "fold"));
            assertThat(Contexts.await(Duration.ofSeconds(20), () -> current(tasks)
                    .map(entry -> entry.value().points() == 5).orElse(false)))
                    .as("the next element starts again from null").isTrue();
        }
    }

    private static Optional<Space.Entry<ItTally>> current(Space tasks) {
        return Reductions.current(tasks, ItTally.class, "tagfold", "fold");
    }

    /**
     * Two peers over TCP, each with an {@code OrderedWiring} naming both as the
     * log's members and a clerk bound to it: every task is confirmed by exactly
     * one clerk and drains from both replicas. One peer cannot do this (a
     * single-member Raft log never elects itself), so the fleet is the test.
     */
    @Test
    @Timeout(180)
    void anOrderedTakeConfirmsEachTaskOnceThroughTheLogAcrossTwoPeers() throws IOException {
        Path keystores = Files.createTempDirectory("ordered-keys");
        PeerIdentity first = FileKeystore.loadOrCreate(keystores.resolve("a"));
        PeerIdentity second = FileKeystore.loadOrCreate(keystores.resolve("b"));
        String members = first.peerId().value() + "," + second.peerId().value();
        int seedPort = Contexts.freePort();
        try (ApplicationContext a = Contexts.run(Contexts.passthrough(seedPort), Map.of(
                "test.app", "ordered-clerk", "agentspaces.keystore", keystores.resolve("a").toString(),
                "test.ordered.members", members, "test.ordered.seed", "1"));
             ApplicationContext b = Contexts.run(Contexts.passthrough(Contexts.freePort(), seedPort), Map.of(
                "test.app", "ordered-clerk", "agentspaces.keystore", keystores.resolve("b").toString(),
                "test.ordered.members", members, "test.ordered.seed", "2"))) {
            assertThat(a.getBean(PeerIdentity.class).peerId()).isEqualTo(first.peerId());
            assertThat(b.getBean(PeerIdentity.class).peerId()).isEqualTo(second.peerId());
            assertThat(a.getBean(FleetEnroller.class).enrolled()).singleElement().isInstanceOf(OrderedClerk.class);
            assertThat(b.getBean(FleetEnroller.class).enrolled()).singleElement().isInstanceOf(OrderedClerk.class);
            Space tasks = space(a, "tasks");
            tasks.write(new ItTask("first", 1), TEN_MINUTES);
            tasks.write(new ItTask("second", 2), TEN_MINUTES);

            Space findings = space(a, "findings");
            assertThat(Contexts.await(Duration.ofSeconds(120), () ->
                    findings.readAll(Template.of(ItFinding.class), 10).size() == 2)).isTrue();
            assertThat(findings.readAll(Template.of(ItFinding.class), 10))
                    .containsExactlyInAnyOrder(new ItFinding("first", "ordered: first"),
                            new ItFinding("second", "ordered: second"));
            List<String> confirmed = new ArrayList<>(a.getBean(OrderedClerk.class).confirmed);
            confirmed.addAll(b.getBean(OrderedClerk.class).confirmed);
            assertThat(confirmed).as("each task confirmed by exactly one clerk")
                    .containsExactlyInAnyOrder("first", "second");
            assertThat(Contexts.await(Duration.ofSeconds(30), () ->
                    tasks.readAll(Template.of(ItTask.class), 10).isEmpty()
                            && space(b, "tasks").readAll(Template.of(ItTask.class), 10).isEmpty()))
                    .as("drained from both replicas").isTrue();
        }
    }

    @Test
    @Timeout(60)
    void aProvidesCapabilityBeanIsEnrolledAndAdvertised() {
        try (ApplicationContext ctx = Contexts.run(Contexts.passthrough(Contexts.freePort()),
                Map.of("test.app", "it-capability"))) {
            assertThat(ctx.getBean(FleetEnroller.class).enrolled()).singleElement().isInstanceOf(ItCapability.class);
            AgentSpaces spaces = ctx.getBean(AgentSpaces.class);
            assertThat(Contexts.await(Duration.ofSeconds(20), () -> !spaces.group("it-fleet").discovery()
                    .find(CapabilityAdvertisement.class, ad -> "aspace:cap/it".equals(ad.capabilityType()))
                    .isEmpty())).isTrue();
            assertThat(spaces.group("it-fleet").discovery()
                    .find(CapabilityAdvertisement.class, ad -> "aspace:cap/it".equals(ad.capabilityType())))
                    .singleElement().satisfies(ad -> {
                        assertThat(ad.issuer()).isEqualTo(ctx.getBean(PeerIdentity.class).peerId());
                        assertThat(ad.parameters()).containsEntry("suite", "micronaut");
                    });
            assertThat(spaces.group("it-fleet").provider("aspace:cap/it")).isPresent();
        }
    }
}
