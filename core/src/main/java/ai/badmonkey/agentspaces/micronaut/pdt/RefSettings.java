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

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

/**
 * One ref's keys, {@code agentspaces.pdt.refs.<name>.*} (srad/agentspaces-pdt.md
 * appendix C): the concurrency mode of PD-R-04, {@code multi} or {@code linear}
 * for the take-and-complete swap of the {@code @SpaceReduce} pattern, and the
 * ref's own follow, {@code full} or {@code lazy} (PDT 2 phase 7, P2-X-08). A key
 * the block leaves out leaves the ref to the facade: a mode from the first head,
 * else {@code multi}, and the facade's {@code agentspaces.pdt.follow}. Micronaut
 * makes one of these per named block.
 */
@EachProperty("agentspaces.pdt.refs")
public class RefSettings {

    private final String name;
    private String mode;
    private String follow;

    public RefSettings(@Parameter String name) {
        this.name = name;
    }

    /** @return the ref name, the block's key */
    public String getName() {
        return name;
    }

    /** @return {@code multi} or {@code linear}, or null when the block names no mode */
    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode == null ? null : mode.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** @return {@code full} or {@code lazy}, or null when the block names no follow */
    public String getFollow() {
        return follow;
    }

    public void setFollow(String follow) {
        this.follow = follow == null ? null : follow.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
