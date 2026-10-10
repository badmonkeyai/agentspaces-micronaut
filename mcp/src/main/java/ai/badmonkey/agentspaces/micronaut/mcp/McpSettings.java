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
package ai.badmonkey.agentspaces.micronaut.mcp;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** {@code agentspaces.micronaut.mcp.*}: the export and the import. */
@ConfigurationProperties("agentspaces.micronaut.mcp")
public class McpSettings {

    private Export export = new Export();
    private Import importSettings = new Import();

    public Export getExport() {
        return export;
    }

    public void setExport(Export export) {
        this.export = export;
    }

    public Import getImport() {
        return importSettings;
    }

    public void setImport(Import importSettings) {
        this.importSettings = importSettings;
    }

    /**
     * Fleet tools published through the MCP server: the catalogue's remote
     * actions (filtered by {@code include-agents} and {@code exclude-agents}),
     * plus this peer's own capability tools and discovery tools when their
     * flags are on.
     */
    @ConfigurationProperties("export")
    public static class Export {
        private boolean enabled;
        private List<String> includeAgents = new ArrayList<>();
        private List<String> excludeAgents = new ArrayList<>();
        private CapabilityTools capabilityTools = new CapabilityTools();
        private DiscoveryTools discoveryTools = new DiscoveryTools();

        public CapabilityTools getCapabilityTools() {
            return capabilityTools;
        }

        public void setCapabilityTools(CapabilityTools capabilityTools) {
            this.capabilityTools = capabilityTools;
        }

        public DiscoveryTools getDiscoveryTools() {
            return discoveryTools;
        }

        public void setDiscoveryTools(DiscoveryTools discoveryTools) {
            this.discoveryTools = discoveryTools;
        }

        /**
         * The capability tools as MCP tools: {@code vote} exports
         * {@code propose_vote}, {@code cast_ballot}, {@code read_tally}, and
         * {@code read_decision}; {@code aggregate} exports {@code contribute}
         * and {@code read_estimate}. Both off by default; an MCP client that
         * calls them acts as this peer's agent.
         */
        @ConfigurationProperties("capability-tools")
        public static class CapabilityTools {
            private boolean vote;
            private boolean aggregate;
            private Duration ballotLease = Duration.ofHours(1);
            private Duration settleTimeout = Duration.ofSeconds(10);

            public boolean isVote() {
                return vote;
            }

            public void setVote(boolean vote) {
                this.vote = vote;
            }

            public boolean isAggregate() {
                return aggregate;
            }

            public void setAggregate(boolean aggregate) {
                this.aggregate = aggregate;
            }

            public Duration getBallotLease() {
                return ballotLease;
            }

            public void setBallotLease(Duration ballotLease) {
                this.ballotLease = ballotLease;
            }

            public Duration getSettleTimeout() {
                return settleTimeout;
            }

            public void setSettleTimeout(Duration settleTimeout) {
                this.settleTimeout = settleTimeout;
            }

            public boolean any() {
                return vote || aggregate;
            }
        }

        /**
         * The discovery tools as MCP tools: {@code agents} exports
         * {@code find_fleet_agents}, {@code assets} exports
         * {@code list_fleet_assets}, {@code data} exports
         * {@code fetch_fleet_data}. All off by default.
         */
        @ConfigurationProperties("discovery-tools")
        public static class DiscoveryTools {
            private boolean agents;
            private boolean assets;
            private boolean data;
            private String dataSpace = "data";
            private Duration timeout = Duration.ofSeconds(10);
            private int maxResultChars = 8000;

            public boolean isAgents() {
                return agents;
            }

            public void setAgents(boolean agents) {
                this.agents = agents;
            }

            public boolean isAssets() {
                return assets;
            }

            public void setAssets(boolean assets) {
                this.assets = assets;
            }

            public boolean isData() {
                return data;
            }

            public void setData(boolean data) {
                this.data = data;
            }

            public String getDataSpace() {
                return dataSpace;
            }

            public void setDataSpace(String dataSpace) {
                this.dataSpace = dataSpace;
            }

            public Duration getTimeout() {
                return timeout;
            }

            public void setTimeout(Duration timeout) {
                this.timeout = timeout;
            }

            public int getMaxResultChars() {
                return maxResultChars;
            }

            public void setMaxResultChars(int maxResultChars) {
                this.maxResultChars = maxResultChars;
            }

            public boolean any() {
                return agents || assets || data;
            }
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
    }

    /** External MCP servers' tools as a fleet agent's actions. */
    @ConfigurationProperties("import")
    public static class Import {
        private boolean enabled;
        private String space = "mcp-calls";
        private Duration lease = Duration.ofMinutes(5);
        private Duration resultLease = Duration.ofMinutes(10);
        private List<String> servers = new ArrayList<>();

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

        public Duration getLease() {
            return lease;
        }

        public void setLease(Duration lease) {
            this.lease = lease;
        }

        /** @return how long a result stays readable in the import space after the take completes */
        public Duration getResultLease() {
            return resultLease;
        }

        public void setResultLease(Duration resultLease) {
            this.resultLease = resultLease;
        }

        public List<String> getServers() {
            return servers;
        }

        public void setServers(List<String> servers) {
            this.servers = servers == null ? new ArrayList<>() : servers;
        }
    }
}
