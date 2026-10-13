# agentspaces-micronaut-langchain4j

The fleet through LangChain4j's extension points on Micronaut 5.2. Add it
beside `agentspaces-micronaut` and the `agentspaces.*` properties, and every
`@AiService` can call agents anywhere on the fleet as tools, a worker's take
lease survives a long model loop, chat memory lives in a replicated space,
model access becomes a fleet service, semantic discovery ranks with a real
embedding model, and token usage sums across the fleet. The wire records are
shared with `agentspaces-springai`, so a Micronaut peer and a Spring Boot peer
serve each other's model calls and resume each other's conversations.

```xml
<dependency>
  <groupId>ai.badmonkey.agentspaces</groupId>
  <artifactId>agentspaces-micronaut-langchain4j</artifactId>
  <version>0.3.0</version>
</dependency>
<!-- and, on the annotation-processor path, micronaut-langchain4j-processor -->
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("ai.badmonkey.agentspaces:agentspaces-micronaut-langchain4j:0.3.0")
    annotationProcessor("io.micronaut.langchain4j:micronaut-langchain4j-processor")
}
```

```groovy
// build.gradle
dependencies {
    implementation 'ai.badmonkey.agentspaces:agentspaces-micronaut-langchain4j:0.3.0'
    annotationProcessor 'io.micronaut.langchain4j:micronaut-langchain4j-processor'
}
```

## Fleet tools

`FleetToolProvider` is a LangChain4j `ToolProvider`: on every service
invocation it returns the fleet's current foreign AgentCards as tools
(`fleet_<agent>_<InputType>`, with a JSON schema from the input record), so
the tool set is live by construction. micronaut-langchain4j never looks a
`ToolProvider` up on its own, so the bridge applies it to every `@AiService`
as it is built (`tools.auto-apply`, default on). To choose per service, set
`auto-apply: false` and name `FleetAiServiceCustomizer` in
`@AiService(customizer = ...)`; `@AgenticService` agents receive the provider
by themselves. A tool call writes the input into the agent's take space and
awaits the result there, so a worker invoked as a tool completes in its take
space (no `resultSpace`).

```java
@AiService
interface Orchestrator {
    @SystemMessage("You coordinate a fleet of specialist agents.")
    String plan(@UserMessage String request);
}
```

The catalogue the tools come from is the base library's
(`agentspaces.micronaut.tools.*`: prefix, include and exclude agents,
`require-attested`, timeout, `on-timeout`, refresh interval, `max`).
`discovery-tools.agents`, `.assets`, and `.data` add `find_fleet_agents`,
`list_fleet_assets`, and `fetch_fleet_data`, each off by default.

The capability tools put layer 4 in the model's hands. With
`capability-tools.vote: true` the provider adds `propose_vote`,
`cast_ballot`, `read_tally`, and `read_decision`; with
`capability-tools.aggregate: true` it adds `contribute` and `read_estimate`.
Both are off by default. The model acts as this peer's agent through the
vote and aggregate clients the group resolves, so a ballot it casts is that
agent's attested record and a contribution joins the group's epoch.
`FleetCapabilityTools` is a bean whenever either flag is on, for code that
calls the same operations directly.

```yaml
agentspaces:
  micronaut:
    langchain4j:
      capability-tools: { vote: true, aggregate: true, ballot-lease: 1h, settle-timeout: 10s }
```

## Workers

An `@AiService` interface can be the worker itself. The binder reads the
fleet annotations through the proxy's interface (`AgentBinder`'s supertype
lookup), so an interface whose methods carry `@SpaceTake` or `@Ballot`
binds as a worker or a voter when handed to `AgentSpaces.bind`: the model
summarizes what the space hands it, or judges each proposal, with no loop
written. The method's parameter is the entry type and its return (a record
for a JSON answer, a `String` for a ballot) is what the binder writes back.

`TakeLeaseListener` is a `ChatModelListener` that renews the in-flight take
on every model request and response, and on an interval in between, so
`@SpaceTake(lease = "2m")` suits a loop that runs for minutes and a dead
worker's task reappears in two. `ChatModelListener` beans reach OpenAI
models automatically; the bridge attaches them to Ollama and Anthropic
builders too, and `FleetChatModel` applies them itself. Another provider
needs one line:

```java
@Singleton
class MyBuilderListener implements BeanCreatedEventListener<MyChatModel.Builder> {
    MyBuilderListener(List<ChatModelListener> listeners) { ... }
    public MyChatModel.Builder onCreated(BeanCreatedEvent<MyChatModel.Builder> e) { return e.getBean().listeners(listeners); }
}
```

With `memory.enabled`, `SpaceChatMemoryStore` (`@Named("fleet")`) keeps each
conversation as its latest leased snapshot in `memory.space`, and
`FleetChatMemoryProvider` (`@Named("fleet")`) keys it by the in-flight take
(`conversation-id: take`; `agent`, `explicit`). A service picks it with
`@AiService("fleet")`; `memory.auto-apply: true` gives it to every service.

## Model access as a fleet service

`model-server.enabled` on a peer that holds provider credentials serves its
`ChatModel` beans to the fleet: requests are taken from `model-server.space`,
by the models the peer serves (named by each bean's default model name;
`models` narrows, `model-beans` maps) or by price under AUCTION, with the
take lease renewed while the provider runs, streaming in chunks, and an
attempt counter a restarted request carries.

`model-client.enabled` on a peer with no key gives it `FleetChatModel` and
`FleetStreamingChatModel` (`@Named("fleet")`, and the default when the peer
has no provider module): one request and one response per round-trip through
the space, trusted only from issuers the authorizer permits `MODEL_SERVE`, the
caller's own tools executed by the caller. Streams reorder chunks, fill gaps,
and follow a server's death by `stream.on-restart` (`fail` with the partial
text, or `restart`).

## Embeddings and usage

`embedder.type: langchain4j` ranks semantic discovery with the peer's
`EmbeddingModel` bean (normalized, cached, identity `langchain4j:<model>` on
the capability advertisement). `usage.enabled` records every response's
token usage per model and agent, sums it across the fleet through the
aggregate capability, writes an optional leased entry per call into
`usage.metrics-space`, and adds a console panel.

## Patterns

No new API, only the chapter 1 annotations beside LangChain4j:

- **A panel of judges.** A `@Ballot` method asks an `@AiService` (a different
  model per peer) whether the proposal holds and returns its verdict; the
  vote capability counts signed, auditable ballots and `@OnDecision` accepts
  the result.
- **Priced by tokens.** A `@BidFunction` prices a task from the peer's model
  price and the prompt's length, so an AUCTION space routes each task to the
  cheapest capable model; `model-server.routing: price` does the same for
  model requests.
- **An extraction ensemble.** Several `@SpaceNotify` extractors over
  different models write candidates for the same entry, and a vote reconciles
  them.
- **An agentic workflow over the fleet.** A `@SequenceAgent` whose
  `@Agent` steps use fleet tools fans work out across the fleet and gathers
  it back; `@AgenticService` agents receive `FleetToolProvider` on their own.
- **A workflow in annotations.** The 0.3.0 set bind here as on Spring: a
  `@SpaceJoin` gathers the parts several model workers wrote under one key
  and hands them to one method; a `@Propose` turns a cue entry into a vote;
  an `@OnEstimate` reacts once a push-sum settles; a `@SpaceReduce` (on a
  `@SpaceAgent`) folds a stream of entries into one accumulator.
- **A model that votes.** With `capability-tools.vote` on, the model opens
  the proposal, casts this peer's ballot, and reads the decision through
  tools, while `@Ballot` methods on other peers answer the same vote.

## Properties

| Property | Default |
| --- | --- |
| `agentspaces.micronaut.langchain4j.tools.enabled` / `auto-apply` | `true` / `true` |
| `...discovery-tools.agents` / `assets` / `data` / `data-space` / `timeout` / `max-result-chars` | `false` ×3 / `data` / `10s` / `8000` |
| `...capability-tools.vote` / `aggregate` / `ballot-lease` / `settle-timeout` | `false` / `false` / `1h` / `10s` |
| `...take-lease.enabled` / `stream-renew-interval` | `true` / `10s` |
| `...memory.enabled` / `auto-apply` / `space` / `ttl` / `conversation-id` / `max-messages` | `false` / `false` / `conversations` / `24h` / `take` / `20` |
| `...model-client.enabled` / `space` / `model` / `timeout` / `stream.on-restart` | `false` / `model-requests` / `""` / `120s` / `fail` |
| `...model-server.enabled` / `space` / `models` / `model-beans` / `routing` / `prices` / `lease` / `chunk-interval` / `chunk-max-deltas` / `chunk-lease` / `response-lease` / `concurrency` | `false` / `model-requests` / all / none / `model` / none / `2m` / `100ms` / `32` / `2m` / `10m` / `4` |
| `...embedder.type` / `model` / `cache-size` | `hashing` / `""` / `4096` |
| `...usage.enabled` / `metrics-space` / `entry-lease` / `publish-interval` | `false` / `""` / `24h` / `30s` |

Every space named here must be registered under `agentspaces.groups[].spaces`.

## Where the behavior lives

Since 2026-10-08 this module is Micronaut wiring over
[`agentspaces-langchain4j`](https://github.com/badmonkeyai/agentspaces-langchain4j):
`FleetToolProvider`, `FleetDiscoveryTools`, `TakeLeaseListener`,
`SpaceChatMemoryStore`, `FleetChatMemoryProvider`, `FleetChatModel`,
`FleetStreamingChatModel`, `ModelServer`, `LangChain4jEmbedder`, and the usage
classes are that library's, in `ai.badmonkey.agentspaces.langchain4j.*`;
`LangChain4jFactory` builds them from these properties and
`FleetAiServicesListener`, `FleetAiServiceCustomizer`, and
`ProviderBuilderListeners` stay here as the Micronaut-specific part. Every
bean name and property is unchanged. The replaceable interfaces
(`ConversationIdResolver`, `ModelCatalog`, `ModelRequestRouter`,
`StreamChunkingPolicy`, `StreamRestartPolicy`, `UsageSink`) moved with the
library and keep deprecated aliases in this module's packages for one
release, so a bean of the old type still replaces the default. The nested
enums of `LangChain4jSettings` (`ConversationIdMode`, `RestartMode`,
`Routing`) are now the library's; a reference by their old nested name
needs the import changed. A plain-Java application uses the same classes
through `LangChain4jFleet`.
