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
package ai.badmonkey.agentspaces.micronaut.langchain4j;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code agentspaces.micronaut.langchain4j.*}: one nested class per feature. */
@ConfigurationProperties("agentspaces.micronaut.langchain4j")
public class LangChain4jSettings {

    private Tools tools = new Tools();
    private DiscoveryTools discoveryTools = new DiscoveryTools();
    private TakeLease takeLease = new TakeLease();
    private Memory memory = new Memory();
    private ModelClient modelClient = new ModelClient();
    private ModelServer modelServer = new ModelServer();
    private Embedder embedder = new Embedder();
    private Usage usage = new Usage();

    public Tools getTools() {
        return tools;
    }

    public void setTools(Tools tools) {
        this.tools = tools;
    }

    public DiscoveryTools getDiscoveryTools() {
        return discoveryTools;
    }

    public void setDiscoveryTools(DiscoveryTools discoveryTools) {
        this.discoveryTools = discoveryTools;
    }

    public TakeLease getTakeLease() {
        return takeLease;
    }

    public void setTakeLease(TakeLease takeLease) {
        this.takeLease = takeLease;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public ModelClient getModelClient() {
        return modelClient;
    }

    public void setModelClient(ModelClient modelClient) {
        this.modelClient = modelClient;
    }

    public ModelServer getModelServer() {
        return modelServer;
    }

    public void setModelServer(ModelServer modelServer) {
        this.modelServer = modelServer;
    }

    public Embedder getEmbedder() {
        return embedder;
    }

    public void setEmbedder(Embedder embedder) {
        this.embedder = embedder;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    /** The fleet {@code ToolProvider}; the catalogue itself is {@code agentspaces.micronaut.tools}. */
    @ConfigurationProperties("tools")
    public static class Tools {
        private boolean enabled = true;
        private boolean autoApply = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAutoApply() {
            return autoApply;
        }

        public void setAutoApply(boolean autoApply) {
            this.autoApply = autoApply;
        }
    }

    /** The three discovery tools, each switched on separately. */
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

    /** The take lease renewed on every model round-trip. */
    @ConfigurationProperties("take-lease")
    public static class TakeLease {
        private boolean enabled = true;
        private Duration streamRenewInterval = Duration.ofSeconds(10);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getStreamRenewInterval() {
            return streamRenewInterval;
        }

        public void setStreamRenewInterval(Duration streamRenewInterval) {
            this.streamRenewInterval = streamRenewInterval;
        }
    }

    /** How a conversation is keyed. */
    public enum ConversationIdMode {
        /** The in-flight take's entry id; falls back to the agent's name. */
        TAKE,
        /** The in-flight take's agent name. */
        AGENT,
        /** Whatever memory id the caller passes. */
        EXPLICIT
    }

    /** Chat memory in a replicated space. */
    @ConfigurationProperties("memory")
    public static class Memory {
        private boolean enabled;
        private boolean autoApply;
        private String space = "conversations";
        private Duration ttl = Duration.ofHours(24);
        private ConversationIdMode conversationId = ConversationIdMode.TAKE;
        private int maxMessages = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAutoApply() {
            return autoApply;
        }

        public void setAutoApply(boolean autoApply) {
            this.autoApply = autoApply;
        }

        public String getSpace() {
            return space;
        }

        public void setSpace(String space) {
            this.space = space;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public ConversationIdMode getConversationId() {
            return conversationId;
        }

        public void setConversationId(ConversationIdMode conversationId) {
            this.conversationId = conversationId;
        }

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }
    }

    /** What a restarted stream does. */
    public enum RestartMode {
        FAIL,
        RESTART
    }

    /** The {@code FleetChatModel}. */
    @ConfigurationProperties("model-client")
    public static class ModelClient {
        private boolean enabled;
        private String space = "model-requests";
        private String model = "";
        private Duration timeout = Duration.ofSeconds(120);
        private Stream stream = new Stream();

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

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model == null ? "" : model;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public Stream getStream() {
            return stream;
        }

        public void setStream(Stream stream) {
            this.stream = stream;
        }

        /** Streaming behavior. */
        @ConfigurationProperties("stream")
        public static class Stream {
            private RestartMode onRestart = RestartMode.FAIL;

            public RestartMode getOnRestart() {
                return onRestart;
            }

            public void setOnRestart(RestartMode onRestart) {
                this.onRestart = onRestart;
            }
        }
    }

    /** How requests are routed to servers. */
    public enum Routing {
        MODEL,
        PRICE
    }

    /** The {@code ModelServer}. */
    @ConfigurationProperties("model-server")
    public static class ModelServer {
        private boolean enabled;
        private String space = "model-requests";
        private List<String> models = new ArrayList<>();
        private Map<String, String> modelBeans = new LinkedHashMap<>();
        private Routing routing = Routing.MODEL;
        private Map<String, Double> prices = new LinkedHashMap<>();
        private Duration lease = Duration.ofMinutes(2);
        private Duration chunkInterval = Duration.ofMillis(100);
        private int chunkMaxDeltas = 32;
        private Duration chunkLease = Duration.ofMinutes(2);
        private Duration responseLease = Duration.ofMinutes(10);
        private int concurrency = 4;

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

        public List<String> getModels() {
            return models;
        }

        public void setModels(List<String> models) {
            this.models = models == null ? new ArrayList<>() : models;
        }

        public Map<String, String> getModelBeans() {
            return modelBeans;
        }

        public void setModelBeans(Map<String, String> modelBeans) {
            this.modelBeans = modelBeans == null ? new LinkedHashMap<>() : modelBeans;
        }

        public Routing getRouting() {
            return routing;
        }

        public void setRouting(Routing routing) {
            this.routing = routing;
        }

        public Map<String, Double> getPrices() {
            return prices;
        }

        public void setPrices(Map<String, Double> prices) {
            this.prices = prices == null ? new LinkedHashMap<>() : prices;
        }

        public Duration getLease() {
            return lease;
        }

        public void setLease(Duration lease) {
            this.lease = lease;
        }

        public Duration getChunkInterval() {
            return chunkInterval;
        }

        public void setChunkInterval(Duration chunkInterval) {
            this.chunkInterval = chunkInterval;
        }

        public int getChunkMaxDeltas() {
            return chunkMaxDeltas;
        }

        public void setChunkMaxDeltas(int chunkMaxDeltas) {
            this.chunkMaxDeltas = chunkMaxDeltas;
        }

        public Duration getChunkLease() {
            return chunkLease;
        }

        public void setChunkLease(Duration chunkLease) {
            this.chunkLease = chunkLease;
        }

        public Duration getResponseLease() {
            return responseLease;
        }

        public void setResponseLease(Duration responseLease) {
            this.responseLease = responseLease;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }
    }

    /** Which embedder ranks semantic discovery. */
    public enum EmbedderType {
        HASHING,
        LANGCHAIN4J
    }

    /** The embedder. */
    @ConfigurationProperties("embedder")
    public static class Embedder {
        private EmbedderType type = EmbedderType.HASHING;
        private String model = "";
        private int cacheSize = 4096;

        public EmbedderType getType() {
            return type;
        }

        public void setType(EmbedderType type) {
            this.type = type;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model == null ? "" : model;
        }

        public int getCacheSize() {
            return cacheSize;
        }

        public void setCacheSize(int cacheSize) {
            this.cacheSize = cacheSize;
        }
    }

    /** Fleet-wide usage. */
    @ConfigurationProperties("usage")
    public static class Usage {
        private boolean enabled;
        private String metricsSpace = "";
        private Duration entryLease = Duration.ofHours(24);
        private Duration publishInterval = Duration.ofSeconds(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMetricsSpace() {
            return metricsSpace;
        }

        public void setMetricsSpace(String metricsSpace) {
            this.metricsSpace = metricsSpace == null ? "" : metricsSpace;
        }

        public Duration getEntryLease() {
            return entryLease;
        }

        public void setEntryLease(Duration entryLease) {
            this.entryLease = entryLease;
        }

        public Duration getPublishInterval() {
            return publishInterval;
        }

        public void setPublishInterval(Duration publishInterval) {
            this.publishInterval = publishInterval;
        }
    }
}
