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
package ai.badmonkey.agentspaces.micronaut.assets;

import ai.badmonkey.agentspaces.api.ad.AssetCard;
import ai.badmonkey.agentspaces.common.id.GroupId;
import ai.badmonkey.agentspaces.common.id.PeerId;
import ai.badmonkey.agentspaces.connect.AssetProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every provider of this peer behind one {@link AssetProvider}, so the peer
 * runs one connector runtime on the data space. Two runtimes on one space
 * would race for the same query entry, and the wrong one would answer; here
 * a query goes to the provider that described the asset. Providers may join
 * after start (the service card does, once the server has bound).
 */
public final class CompositeAssetProvider implements AssetProvider {

    private final List<AssetProvider> providers = new CopyOnWriteArrayList<>();
    private final Map<String, AssetProvider> byAsset = new ConcurrentHashMap<>();

    public void add(AssetProvider provider) {
        providers.add(Objects.requireNonNull(provider, "provider"));
    }

    public List<AssetProvider> providers() {
        return List.copyOf(providers);
    }

    @Override
    public List<AssetCard> describeAssets(GroupId group, PeerId issuer, Instant now) {
        List<AssetCard> cards = new ArrayList<>();
        for (AssetProvider provider : providers) {
            for (AssetCard card : provider.describeAssets(group, issuer, now)) {
                AssetProvider previous = byAsset.put(card.asset(), provider);
                if (previous != null && previous != provider) {
                    throw new IllegalStateException("two providers describe the asset '"
                            + card.asset() + "': " + previous.getClass().getName() + " and "
                            + provider.getClass().getName() + "; give one a different name");
                }
                cards.add(card);
            }
        }
        return cards;
    }

    @Override
    public QueryResult query(String asset, Map<String, String> parameters) {
        AssetProvider provider = byAsset.get(asset);
        if (provider == null) {
            return QueryResult.failed("no provider on this peer describes the asset '" + asset + "'");
        }
        return provider.query(asset, parameters);
    }
}
