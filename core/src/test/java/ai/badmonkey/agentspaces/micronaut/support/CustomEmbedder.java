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

import ai.badmonkey.agentspaces.api.spi.Embedder;
import ai.badmonkey.agentspaces.capabilities.semantic.HashingEmbedder;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/** An application-supplied Embedder: the factory's default must yield to it. */
@Singleton
@Requires(property = "test.custom-embedder", value = "true")
public class CustomEmbedder implements Embedder {

    private final Embedder delegate = new HashingEmbedder();

    @Override
    public double[] embed(String text) {
        return delegate.embed(text);
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }
}
