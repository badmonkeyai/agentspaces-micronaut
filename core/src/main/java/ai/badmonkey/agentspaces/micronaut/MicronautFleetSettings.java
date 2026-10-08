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

import io.micronaut.context.annotation.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The settings this project adds under {@code agentspaces.micronaut.*}; the
 * fabric itself is configured by the Spring starter's {@code agentspaces.*}
 * properties, bound by {@link FleetConfiguration}.
 */
@ConfigurationProperties("agentspaces.micronaut")
public class MicronautFleetSettings {

    private String group = "";
    private Enroll enroll = new Enroll();
    private Assets assets = new Assets();
    private ServiceAsset serviceAsset = new ServiceAsset();
    private Registry registry = new Registry();
    private Management management = new Management();
    private Tools tools = new Tools();

    /** @return the group the service's assets, registry, and views work in; blank means the first configured */
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group == null ? "" : group;
    }

    public Enroll getEnroll() {
        return enroll;
    }

    public void setEnroll(Enroll enroll) {
        this.enroll = enroll;
    }

    public Assets getAssets() {
        return assets;
    }

    public void setAssets(Assets assets) {
        this.assets = assets;
    }

    public ServiceAsset getServiceAsset() {
        return serviceAsset;
    }

    public void setServiceAsset(ServiceAsset serviceAsset) {
        this.serviceAsset = serviceAsset;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public Management getManagement() {
        return management;
    }

    public void setManagement(Management management) {
        this.management = management;
    }

    public Tools getTools() {
        return tools;
    }

    public void setTools(Tools tools) {
        this.tools = tools;
    }

    /** {@code agentspaces.micronaut.tools.*}: the fleet tool catalogue the bridges share. */
    @ConfigurationProperties("tools")
    public static class Tools {
        private boolean enabled = true;
        private String prefix = "fleet_";
        private List<String> includeAgents = new ArrayList<>();
        private List<String> excludeAgents = new ArrayList<>();
        private boolean requireAttested;
        private Duration timeout = Duration.ofSeconds(30);
        private ai.badmonkey.agentspaces.micronaut.tools.ToolInvocation.OnTimeout onTimeout =
                ai.badmonkey.agentspaces.micronaut.tools.ToolInvocation.OnTimeout.RESULT;
        private Duration refreshInterval = Duration.ofSeconds(5);
        private int max = 64;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix == null ? "" : prefix;
        }

        public List<String> getIncludeAgents() {
            return includeAgents;
        }

        public void setIncludeAgents(List<String> includeAgents) {
            this.includeAgents = includeAgents == null ? new ArrayList<>() : includeAgents;
        }

        public List<String> getExcludeAgents() {
            return excludeAgents;
        }

        public void setExcludeAgents(List<String> excludeAgents) {
            this.excludeAgents = excludeAgents == null ? new ArrayList<>() : excludeAgents;
        }

        public boolean isRequireAttested() {
            return requireAttested;
        }

        public void setRequireAttested(boolean requireAttested) {
            this.requireAttested = requireAttested;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public ai.badmonkey.agentspaces.micronaut.tools.ToolInvocation.OnTimeout getOnTimeout() {
            return onTimeout;
        }

        public void setOnTimeout(ai.badmonkey.agentspaces.micronaut.tools.ToolInvocation.OnTimeout onTimeout) {
            this.onTimeout = onTimeout;
        }

        public Duration getRefreshInterval() {
            return refreshInterval;
        }

        public void setRefreshInterval(Duration refreshInterval) {
            this.refreshInterval = refreshInterval;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }
    }

    /** {@code agentspaces.micronaut.enroll.*}: annotated-bean enrollment. */
    @ConfigurationProperties("enroll")
    public static class Enroll {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** {@code agentspaces.micronaut.assets.*}: {@code AssetProvider} beans. */
    @ConfigurationProperties("assets")
    public static class Assets {
        private boolean enabled = true;
        private String space = "data";
        private Duration freshness = Duration.ofMinutes(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSpace() {
            return space;
        }

        public void setSpace(String space) {
            this.space = space;
        }

        public Duration getFreshness() {
            return freshness;
        }

        public void setFreshness(Duration freshness) {
            this.freshness = freshness;
        }
    }

    /** {@code agentspaces.micronaut.service-asset.*}: the embedded server as an asset. */
    @ConfigurationProperties("service-asset")
    public static class ServiceAsset {
        private boolean enabled;
        private String asset = "";
        private String externalUri = "";
        private String description = "";
        private String query = "none";
        private List<String> allowedPaths = new ArrayList<>();
        private Duration queryTimeout = Duration.ofSeconds(5);
        private int maxBodyBytes = 1_048_576;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAsset() {
            return asset;
        }

        public void setAsset(String asset) {
            this.asset = asset == null ? "" : asset;
        }

        public String getExternalUri() {
            return externalUri;
        }

        public void setExternalUri(String externalUri) {
            this.externalUri = externalUri == null ? "" : externalUri;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description == null ? "" : description;
        }

        /** @return {@code none} or {@code get} */
        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query == null ? "none" : query;
        }

        public List<String> getAllowedPaths() {
            return allowedPaths;
        }

        public void setAllowedPaths(List<String> allowedPaths) {
            this.allowedPaths = allowedPaths == null ? new ArrayList<>() : allowedPaths;
        }

        public Duration getQueryTimeout() {
            return queryTimeout;
        }

        public void setQueryTimeout(Duration queryTimeout) {
            this.queryTimeout = queryTimeout;
        }

        public int getMaxBodyBytes() {
            return maxBodyBytes;
        }

        public void setMaxBodyBytes(int maxBodyBytes) {
            this.maxBodyBytes = maxBodyBytes;
        }
    }

    /** {@code agentspaces.micronaut.registry.*}: the fleet as a service registry. */
    @ConfigurationProperties("registry")
    public static class Registry {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** {@code agentspaces.micronaut.management.*}: health, info, endpoint, meters. */
    @ConfigurationProperties("management")
    public static class Management {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
