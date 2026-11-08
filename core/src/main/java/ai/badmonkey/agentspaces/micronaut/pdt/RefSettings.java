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
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

/**
 * One ref's keys, {@code agentspaces.pdt.refs.<name>.*} (srad/agentspaces-pdt.md
 * appendix C): the concurrency mode of PD-R-04, {@code multi} by default or
 * {@code linear} for the take-and-complete swap of the {@code @SpaceReduce}
 * pattern. Micronaut makes one of these per named block.
 */
@EachProperty("agentspaces.pdt.refs")
public class RefSettings {

    private final String name;
    private String mode = PRef.MODE_MULTI;

    public RefSettings(@Parameter String name) {
        this.name = name;
    }

    /** @return the ref name, the block's key */
    public String getName() {
        return name;
    }

    /** @return {@code multi} or {@code linear} */
    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode == null ? PRef.MODE_MULTI : mode.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
