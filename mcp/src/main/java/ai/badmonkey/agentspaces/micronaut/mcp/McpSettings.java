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

    /** Fleet tools published through the MCP server. */
    @ConfigurationProperties("export")
    public static class Export {
        private boolean enabled;
        private List<String> includeAgents = new ArrayList<>();
        private List<String> excludeAgents = new ArrayList<>();

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
