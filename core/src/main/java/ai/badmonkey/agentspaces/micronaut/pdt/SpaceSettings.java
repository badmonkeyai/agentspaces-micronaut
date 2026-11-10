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
import io.micronaut.core.convert.format.ReadableBytes;

/**
 * One {@code agentspaces.pdt.spaces.<name>} block (srad/agentspaces-pdt.md §3.7, PD-E-01, PD-E-06; PDT 2 phase 7,
 * P2-X-04): the space named by the key escalates an entry above {@code escalate-above} bytes through the persistent
 * trees, with the flat swarm as its fallback unless {@code require} is true. The shared wiring builds the space
 * before the factory's facade exists, so it escalates through the holder's deferred seam, which reaches the facade
 * once the factory adopts it.
 */
@EachProperty("agentspaces.pdt.spaces")
public class SpaceSettings {

    private final String name;
    private long escalateAbove;
    private boolean require;

    public SpaceSettings(@Parameter String name) {
        this.name = name;
    }

    /** @return the space name, the block's key */
    public String getName() {
        return name;
    }

    /** @return the bytes above which an entry escalates through the trees; 0 leaves the space as it is */
    public long getEscalateAbove() {
        return escalateAbove;
    }

    public void setEscalateAbove(@ReadableBytes long escalateAbove) {
        this.escalateAbove = escalateAbove;
    }

    /** @return whether a write that cannot reach every member through the trees is refused rather than sent flat */
    public boolean isRequire() {
        return require;
    }

    public void setRequire(boolean require) {
        this.require = require;
    }
}
