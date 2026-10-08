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
package ai.badmonkey.agentspaces.examples.micronaut.analyst;

import ai.badmonkey.agentspaces.agent.annotation.SpaceNotify;
import ai.badmonkey.agentspaces.examples.micronaut.PricedOrder;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A plain singleton with only a method annotation: no {@code @SpaceAgent},
 * no {@code @AgentSpec}. It enrolls because the example compiles with
 * {@code agentspaces-micronaut-processor}, which marks the method executable
 * at compile time.
 */
@Singleton
@Requires(env = "analyst")
public class FindingsWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(FindingsWatcher.class);

    private final List<PricedOrder> seen = new CopyOnWriteArrayList<>();

    @SpaceNotify(space = "findings")
    public void onPriced(PricedOrder priced) {
        LOG.info("order {} priced at {} by {}", priced.orderId(), priced.total(), priced.pricedBy());
        seen.add(priced);
    }

    public List<PricedOrder> seen() {
        return List.copyOf(seen);
    }
}
