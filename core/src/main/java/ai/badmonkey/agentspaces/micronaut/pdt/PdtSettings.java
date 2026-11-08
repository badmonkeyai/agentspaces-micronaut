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
package ai.badmonkey.agentspaces.micronaut.pdt;

import ai.badmonkey.agentspaces.agent.pdt.PRef;
import ai.badmonkey.agentspaces.pdt.Pdt;
import ai.badmonkey.agentspaces.pdt.refs.Refs;
import ai.badmonkey.agentspaces.pdt.store.Retention;
import ai.badmonkey.agentspaces.pdt.store.StoreThenFetchLoader;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.ReadableBytes;

import java.time.Duration;

/**
 * The {@code agentspaces.pdt.*} keys of srad/agentspaces-pdt.md §7.2 and
 * appendix C, bound by this project (PD-C-03) with the same names and defaults
 * the Spring starter reads (PD-C-02). Byte sizes take Micronaut's readable
 * form ({@code 32KB}, {@code 20GB}); durations take {@code 10s}, {@code 1h},
 * or ISO-8601. Per-ref modes bind through {@link RefSettings}.
 */
@ConfigurationProperties("agentspaces.pdt")
public class PdtSettings {

    private boolean enabled;
    private String space = "refs";
    private int inlineDelta = PRef.INLINE_DELTA_BYTES;
    private Duration loadTimeout = StoreThenFetchLoader.DEFAULT_DEADLINE;
    private String follow = Pdt.FOLLOW_FULL;
    private String sealing = PRef.SEALING_NONE;
    private Duration headLease = Refs.DEFAULT_HEAD_LEASE;
    private Duration cancelGrace = Duration.ofMillis(Refs.CANCEL_GRACE_MILLIS);
    private Duration collectEvery = Duration.ofMinutes(1);
    private Store store = new Store();
    private Retain retain = new Retain();
    private Take take = new Take();

    /** @return whether a {@code Pdt} is built for every group ({@code agentspaces.pdt.enabled}) */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return the registered space of each group that holds the ref heads
     *         ({@code agentspaces.pdt.space}); it must appear in the group's
     *         {@code spaces} so the space's own admission governs who writes a head (PD-P-04)
     */
    public String getSpace() {
        return space;
    }

    public void setSpace(String space) {
        this.space = space == null ? "refs" : space.trim();
    }

    /** @return the largest delta carried on a head, in bytes ({@code agentspaces.pdt.inline-delta}, PD-X-07) */
    public int getInlineDelta() {
        return inlineDelta;
    }

    public void setInlineDelta(@ReadableBytes int inlineDelta) {
        this.inlineDelta = inlineDelta;
    }

    /** @return how long a lazy load or a deref waits for a node ({@code agentspaces.pdt.load-timeout}, PD-V-06) */
    public Duration getLoadTimeout() {
        return loadTimeout;
    }

    public void setLoadTimeout(Duration loadTimeout) {
        this.loadTimeout = loadTimeout;
    }

    /** @return {@code full} or {@code lazy} ({@code agentspaces.pdt.follow}, PD-X-10) */
    public String getFollow() {
        return follow;
    }

    public void setFollow(String follow) {
        this.follow = follow == null ? Pdt.FOLLOW_FULL : follow.trim();
    }

    /** @return the sealing every head names ({@code agentspaces.pdt.sealing}, §3.8) */
    public String getSealing() {
        return sealing;
    }

    public void setSealing(String sealing) {
        this.sealing = sealing == null ? PRef.SEALING_NONE : sealing.trim();
    }

    /** @return the write lease of a head, renewed at half-life ({@code agentspaces.pdt.head-lease}, PD-R-08) */
    public Duration getHeadLease() {
        return headLease;
    }

    public void setHeadLease(Duration headLease) {
        this.headLease = headLease;
    }

    /** @return how long a writer keeps a superseded head ({@code agentspaces.pdt.cancel-grace}, PD-R-03) */
    public Duration getCancelGrace() {
        return cancelGrace;
    }

    public void setCancelGrace(Duration cancelGrace) {
        this.cancelGrace = cancelGrace;
    }

    /** @return how often a collection cycle starts; zero disables it ({@code agentspaces.pdt.collect-every}, PD-S-04) */
    public Duration getCollectEvery() {
        return collectEvery;
    }

    public void setCollectEvery(Duration collectEvery) {
        this.collectEvery = collectEvery;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public Retain getRetain() {
        return retain;
    }

    public void setRetain(Retain retain) {
        this.retain = retain;
    }

    public Take getTake() {
        return take;
    }

    public void setTake(Take take) {
        this.take = take;
    }

    /** The node store ({@code agentspaces.pdt.store.*}, PD-S-02, PD-S-05). */
    @ConfigurationProperties("store")
    public static class Store {

        /** A store over pack files under {@link #getPath()}, one directory per group. */
        public static final String PACK = "pack";

        /** A store on the heap. */
        public static final String MEMORY = "memory";

        /** The application's {@code NodeStore} bean. */
        public static final String BEAN = "bean";

        private String kind = PACK;
        private String path = System.getProperty("user.home") + "/.agentspaces/pdt";
        private long quota;
        private boolean require;

        /** @return {@code pack}, {@code memory}, or {@code bean} */
        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind == null ? PACK : kind.trim().toLowerCase(java.util.Locale.ROOT);
        }

        /** @return the pack directory; each group keeps its packs in a subdirectory named for it */
        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        /** @return the store quota in bytes; zero is unbounded */
        public long getQuota() {
            return quota;
        }

        public void setQuota(@ReadableBytes long quota) {
            this.quota = quota;
        }

        /** @return whether to refuse to start without a durable store (PD-Q-AVL-02) */
        public boolean isRequire() {
            return require;
        }

        public void setRequire(boolean require) {
            this.require = require;
        }
    }

    /** Retention of delta bases ({@code agentspaces.pdt.retain.*}, PD-S-03). */
    @ConfigurationProperties("retain")
    public static class Retain {

        private int versions = Retention.DEFAULT_VERSIONS;
        private Duration age = Retention.DEFAULT_AGE;

        /** @return versions kept per ref */
        public int getVersions() {
            return versions;
        }

        public void setVersions(int versions) {
            this.versions = versions;
        }

        /** @return the age kept per ref */
        public Duration getAge() {
            return age;
        }

        public void setAge(Duration age) {
            this.age = age;
        }
    }

    /** A linear swap's take ({@code agentspaces.pdt.take.*}, PD-R-04). */
    @ConfigurationProperties("take")
    public static class Take {

        private Duration lease = Duration.ofSeconds(30);
        private Duration timeout = Duration.ofSeconds(5);
        private int retries = 3;

        /** @return the take lease */
        public Duration getLease() {
            return lease;
        }

        public void setLease(Duration lease) {
            this.lease = lease;
        }

        /** @return how long a take waits for the head before it re-reads */
        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        /** @return how many times a timed-out take retries */
        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }
    }
}
