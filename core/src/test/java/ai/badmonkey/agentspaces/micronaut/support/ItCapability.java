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
package ai.badmonkey.agentspaces.micronaut.support;

import ai.badmonkey.agentspaces.agent.annotation.ProvidesCapability;
import ai.badmonkey.agentspaces.api.ad.CapabilityAdvertisement;
import ai.badmonkey.agentspaces.api.spi.CapabilityProvider;
import ai.badmonkey.agentspaces.common.id.GroupId;
import ai.badmonkey.agentspaces.identity.PeerIdentity;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** A plain singleton capability provider the enroller must find and advertise. */
@Singleton
@ProvidesCapability("aspace:cap/it")
@Requires(property = "test.app", value = "it-capability")
public class ItCapability implements CapabilityProvider {

    private final PeerIdentity identity;

    public ItCapability(PeerIdentity identity) {
        this.identity = identity;
    }

    @Override
    public String capabilityType() {
        return "aspace:cap/it";
    }

    @Override
    public CapabilityAdvertisement describe(GroupId group) {
        return new CapabilityAdvertisement("aspace://" + group.value() + "/cap/it/" + identity.peerId().value(),
                identity.peerId(), group, Instant.now(), Duration.ofMinutes(15),
                "aspace:cap/it", "0.1", "pipe", Map.of("suite", "micronaut"), Map.of());
    }
}
