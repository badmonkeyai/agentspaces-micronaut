# agentspaces-micronaut

## Introducing AgentSpaces by Bad Monkey AI

**A peer-to-peer coordination fabric for agents, services, and the people who work with them.**

AgentSpaces is a P2P framework to quickly discover and join P2P networks, where agents, software services, and people share assets, queries, results, and coordinate. The framework helps you build fleets of agents, tie in other software, and coordinate across them with a replicated tuple space, quorum building, and more. Agents post work, claim tasks, publish results, vote on decisions, and recover from each other's failures. There is no broker, no registry, no scheduler, and no server to keep alive. Coordination is peer-to-peer, secured end to end, and replicated across the fleet.

## Quick start: build your first agent with Micronaut

An agent is a method. Its parameter is the work it takes, and its return value is the next entry in the space. In Micronaut the method sits on a plain `@Singleton`: the space hands it one `Order` at a time under a lease, and the returned `Shipment` completes the order. The `agentspaces-micronaut-processor` annotation processor enrolls the bean at compile time, so the one fleet annotation on the method is all the bean needs.

**1. Add the dependency and the processor.** Micronaut 5 compiles to Java 25 bytecode, so the application runs on JDK 25.

```xml
<dependency>
  <groupId>ai.badmonkey.agentspaces</groupId>
  <artifactId>agentspaces-micronaut</artifactId>
  <version>0.4.0</version>
</dependency>
```

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths combine.children="append">
      <path>
        <groupId>ai.badmonkey.agentspaces</groupId>
        <artifactId>agentspaces-micronaut-processor</artifactId>
        <version>0.4.0</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

**2. Configure the peer.** These are the Spring Boot starter's `agentspaces.*` properties, unchanged. The default `mtls` security profile secures every link, so the file has no security block. The `${shop.*}` placeholders let you start a second instance from the command line.

```yaml
micronaut:
  application:
    name: shop

agentspaces:
  bind: 127.0.0.1:${shop.port:7701}
  groups:
    - name: shop
      founding: shop-v1
      seeds: ${shop.seeds:}      # empty on the first instance
      spaces:
        - name: orders
```

**3. Write the agent.** Two records name the entries, and the bean takes one `Order` and returns a `Shipment`. `main` starts the application and, when you pass an order, writes it and prints the shipment. The listings omit imports.

```java
public record Order(String orderId, String item) {
}

public record Shipment(String orderId, String item, String by) {
}
```

```java
@Singleton
public class Fulfiller {

    @SpaceTake(lease = "30s")
    public Shipment ship(Order order) {
        return new Shipment(order.orderId(), order.item(), "fulfiller");
    }
}
```

```java
public class ShopApp {

    public static void main(String[] args) throws InterruptedException {
        ApplicationContext context = Micronaut.run(ShopApp.class, args);
        String item = context.getProperty("shop.order", String.class).orElse("");
        if (item.isBlank()) {
            Thread.currentThread().join();               // an agent instance: serve until killed
        } else {                                         // a desk instance: write one order, read the shipment
            Space orders = context.getBean(AgentSpaces.class).group("shop").space("orders");
            orders.write(new Order("o-1", item), Lease.of(Duration.ofMinutes(10)));
            orders.read(Template.of(Shipment.class), Duration.ofSeconds(30)).ifPresent(System.out::println);
            context.close();
        }
    }
}
```

**4. Run two instances.** Start the first instance, then a second one that joins through it and writes an order.

```
mvn -q compile exec:java -Dexec.mainClass=shop.ShopApp
mvn -q compile exec:java -Dexec.mainClass=shop.ShopApp -Dexec.args="--shop.port=7702 --shop.seeds=127.0.0.1:7701 --shop.order=kite"
```

The second instance prints the shipment. Either instance can take the order, because both run the same agent.

**5. Kill one.** Add `System.out.println("shipping " + order.orderId());` and `Thread.sleep(10_000);` at the top of `ship` (the method then needs `throws InterruptedException`). Start both instances, write an order from the second, and run `kill -9` on whichever instance prints `shipping`. If you killed the first instance, the second prints the shipment. If you killed the second, the first prints `shipping o-1` again when the 30 second lease lapses.

The sections below add the rest: agents and their return conventions, assets and the fleet as a service registry, management endpoints, and the LangChain4j and MCP bridges.

## About agentspaces-micronaut

AgentSpaces for Micronaut microservices. Add the library and the
`agentspaces.*` properties the Spring Boot starter reads, and the service is a
peer: its annotated beans work the fleet's spaces and publish AgentCards, its
`AssetProvider` beans and its own HTTP presence publish AssetCards, other
services resolve it through `@Client`, and its health and management
endpoints show the fleet. A standalone project on the published AgentSpaces
0.4.0 libraries and Micronaut 5.2. The library is Java 21 bytecode; Micronaut
5.2 itself is Java 25 bytecode, so applications run on JDK 25.

The library is on Maven Central under the group `ai.badmonkey.agentspaces`;
no repository configuration is needed. It releases from this repository on
its own cadence, so it carries its version explicitly.

Maven (`pom.xml`):

```xml
<dependency>
  <groupId>ai.badmonkey.agentspaces</groupId>
  <artifactId>agentspaces-micronaut</artifactId>
  <version>0.4.0</version>
</dependency>
```

Gradle Kotlin DSL (`build.gradle.kts`):

```kotlin
dependencies {
    implementation("ai.badmonkey.agentspaces:agentspaces-micronaut:0.4.0")
}
```

Gradle Groovy DSL (`build.gradle`):

```groovy
dependencies {
    implementation 'ai.badmonkey.agentspaces:agentspaces-micronaut:0.4.0'
}
```

```yaml
micronaut:
  application:
    name: orders
agentspaces:
  keystore: ./peer-keys
  bind: 127.0.0.1:7500
  groups:
    - name: order-fleet
      founding: order-fleet-v1
      seeds: ["hq.example:7500"]
      spaces:
        - name: tasks
        - name: findings
        - name: data
```

Every `agentspaces.*` property is the Spring Boot starter's, with the same
names and defaults (its README is the reference): the security profile,
transports and TLS, groups founded or joined, spaces with admission, content
keys, capabilities, the console, and the A2A gateway. One YAML fragment
configures a peer under either framework. Durations take ISO-8601 (`PT5M`)
or the short form (`5m`, `250ms`).

## How to use AI with AgentSpaces

At the end of this section you will have run three fleets of Micronaut
application contexts in one JVM, over TCP on free loopback ports, with no
network and no model key: a plain `@Singleton` whose one fleet annotation is
on a method, enrolled at compile time, completing work a second service
writes; a LangChain4j `@AiService` on a peer that holds no provider key,
planning with the fleet's agents as its tools and the fleet's model, beside an
MCP gateway that exports the same tools; and an embedded server published as
an AssetCard that an analyst with no network path to it queries through the
space. Each snippet is cut from the named test or example, verbatim with the
imports elided.

### 1. Build an agent and hand it work from another service

The bean carries no `@SpaceAgent` and no `@AgentSpec`: this module's tests
compile with `agentspaces-micronaut-processor`, so the `@SpaceTake` method
alone enrolls it (the startup log reads `enrolled ...$Summarizer
(compile-time, via summarize)`). The second context seeds from the first,
writes a task, and reads the finding the method's return value became.
`Contexts.fleet(port, seedPort)` is the YAML above as a property map: one
founded group, `it-fleet`, with the `tasks`, `findings`, and `data` spaces.
(`core/src/test/.../ReadmeExamplesTest.java`; run
`cd agentspaces-micronaut && mvn -q test -pl core -Dtest=ReadmeExamplesTest`)

```java
@Singleton                                                       // a plain bean: the processor enrolls it at compile time
@Requires(property = "test.app", value = "readme-summarizer")
public static class Summarizer {

    @SpaceTake(space = "tasks", lease = "1m", pollTimeout = "PT0.2S", resultSpace = "findings")
    public ItFinding summarize(ItTask task) {
        return new ItFinding(task.topic(), "summary of " + task.topic());
    }
}

int workerPort = Contexts.freePort();
try (ApplicationContext worker = Contexts.run(Contexts.fleet(workerPort, 0), Map.of("test.app", "readme-summarizer"));
     ApplicationContext writer = Contexts.run(Contexts.fleet(Contexts.freePort(), workerPort), Map.of())) {
    assertThat(worker.getBean(FleetEnroller.class).enrolled()).singleElement().isInstanceOf(Summarizer.class);

    AgentSpaces.GroupContext fleet = writer.getBean(AgentSpaces.class).group("it-fleet");
    fleet.space("tasks").write(new ItTask("tuple spaces", 1), Lease.of(Duration.ofMinutes(10)));
    Optional<ItFinding> finding = fleet.space("findings").read(Template.of(ItFinding.class), Duration.ofSeconds(60));
    assertThat(finding).hasValueSatisfying(f -> assertThat(f.summary()).isEqualTo("summary of tuple spaces"));
}
```

The two services share the space and nothing else: no queue, no registry
entry, and no URL for the agent. A `@Controller` can call `summarize` for
HTTP as well, and the AgentCard lists it as an action any fleet agent
invokes through `RemoteActions`.

### 2. A model shared across peers, with no key on the orchestrator

Example 17 (`examples/micronaut-ai-fleet`) runs three peers. The worker holds
the only model: its summarizer agent calls it directly, and
`model-server.enabled: true` serves it to the fleet. The orchestrator sets
`model-client.enabled: true` and no key: the LangChain4j bridge hands its
`@AiService` the fleet's agents as live tools and routes its model calls
through the space to the worker. The gateway exports the same tools on an
MCP server. The test replaces the provider model with a scripted one, so the
whole loop runs with no API key.
(`Orchestrator.java`, `Workers.java`, `MicronautAiFleetTest.java`, with the worker and gateway
contexts and the closing braces elided; run
`mvn -q test -Pexamples -pl examples/micronaut-ai-fleet -Dtest=MicronautAiFleetTest`)

```java
@AiService
@Requires(env = "orchestrator")
public interface Orchestrator {

    @SystemMessage("You coordinate a fleet of specialist agents. Use the fleet tools to get work done,"
            + " then answer with a short plan.")
    String plan(@UserMessage String request);
}

@SpaceAgent(name = "summarizer", description = "Summarizes a topic into a short briefing", goals = {"summarize"})
@Requires(env = "worker")
public static class Summarizer {
    private final ChatModel model;

    public Summarizer(ChatModel model) {
        this.model = model;
    }

    @SpaceTake(space = "tasks", lease = "2m", pollTimeout = "PT0.2S", description = "Summarizes a topic")
    public Summary summarize(Brief brief) {
        return new Summary(brief.topic(), model.chat("Summarize '" + brief.topic() + "' in "
                + brief.sentences() + " sentences."));
    }
}

// ... the worker context, holding the only model (scripted in the test), then:
ApplicationContext orchestrator = ApplicationContext.run(Map.of(
        "agentspaces.bind", "127.0.0.1:" + freePort(),
        "agentspaces.groups[0].seeds[0]", "127.0.0.1:" + workerPort,
        "langchain4j.open-ai.enabled", "false"), "orchestrator")) {
// ... the MCP gateway context, then:
FleetActions tools = orchestrator.getBean(FleetActions.class);
assertThat(await(Duration.ofSeconds(60), () -> tools.current().size() == 2)).isTrue();

String plan = orchestrator.getBean(Orchestrator.class)
        .plan("Plan a two-day offsite for 12 people.");
assertThat(plan).startsWith("Plan:").contains("team retreats").contains("504.0");
```

The plan comes back through three model round trips served over the space,
and `FleetMcpTools.published()` on the gateway lists `fleet_summarizer_Brief`
and `fleet_pricer_PriceRequest` once the cards arrive (the same test asserts
both). A Spring Boot peer serving the model through `agentspaces-springai`
answers the same requests, because both speak `agentspaces-model-wire`.

### 3. The service itself as an asset card

With `service-asset.enabled` the embedded server publishes an AssetCard once
it has bound: the application name, its URI, shape `http`, freshness `live`.
With `service-asset.query=get` and an allow-list, an analyst peer with no
route to the server fetches an allowed path through the data space with
`DataQueryClient`; a path outside the list is answered with an error naming
the rule. (`core/src/test/.../ServiceAssetTest.theEmbeddedServerPublishesAnAssetCardAndAnswersAllowedGets`;
run `mvn -q test -pl core -Dtest=ServiceAssetTest`)

```java
Map<String, Object> props = new HashMap<>(Contexts.fleet(port, seedPort));
props.put("micronaut.server.port", "-1");
props.put("micronaut.application.name", "orders");
props.put("test.server", "true");
props.put("agentspaces.micronaut.service-asset.enabled", "true");
props.put("agentspaces.micronaut.service-asset.query", query);
props.put("agentspaces.micronaut.service-asset.allowed-paths[0]", "/hello");
return props;

EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, service(seedPort, 0, "get"));
try (ApplicationContext orders = server.getApplicationContext();
     ApplicationContext analyst = Contexts.run(Contexts.fleet(Contexts.freePort(), seedPort), Map.of())) {
    ServiceAssetCard serviceAsset = orders.getBean(ServiceAssetCard.class);
    assertThat(Contexts.await(Duration.ofSeconds(10), () -> serviceAsset.card().isPresent())).isTrue();
    AssetCard card = serviceAsset.card().orElseThrow();
    assertThat(card.asset()).isEqualTo("orders");
    assertThat(card.uri()).isEqualTo(server.getURI().toString());
    assertThat(card.shape()).isEqualTo("http");
    assertThat(card.freshness()).isEqualTo("live");
    assertThat(card.access()).containsEntry("mode", "http").containsEntry("query", "aspace:cap/data-query");

    FleetContext fleet = analyst.getBean(FleetContext.class);
    assertThat(Contexts.await(Duration.ofSeconds(30), () -> !fleet.group().discovery()
            .find(AssetCard.class, c -> "orders".equals(c.asset())).isEmpty())).isTrue();

    DataQueryClient client = new DataQueryClient(fleet.group().space("data"), "analyst",
            analyst.getBean(Authorizer.class));
    assertThat(client.fetch("orders", Map.of("path", "/hello"), Duration.ofSeconds(30)))
            .hasValueSatisfying(fetched -> {
                assertThat(fetched.result().error()).isNull();
                assertThat(fetched.result().rows()).singleElement()
                        .satisfies(row -> {
                            assertThat(row).containsEntry("status", "200");
                            assertThat(row.get("body")).isEqualTo("hello from orders");
                        });
            });
    assertThat(client.fetch("orders", Map.of("path", "/health"), Duration.ofSeconds(30)))
            .hasValueSatisfying(fetched -> assertThat(fetched.result().error())
                    .contains("not allowed"));
```

## Agents

`@SpaceAgent` is a bean-defining stereotype (a singleton) that is also an
`@AgentSpec`. At startup the library finds every bean carrying `@AgentSpec`
(directly or through a stereotype) or `@ProvidesCapability`, instantiates
it, and binds it: worker loops start, bids wire, and the bean's AgentCard
publishes into every joined group whose spaces satisfy its annotations.

```java
@SpaceAgent(name = "orders-service", description = "Prices customer orders")
public class Pricer {

    @SpaceTake(space = "tasks", resultSpace = "findings", lease = "1m")
    public PricedOrder price(PriceRequest request) { ... }
}
```

A `@Controller` can call the same method for HTTP: one implementation, two
front doors, and the AgentCard lists `price` as an action any fleet agent
invokes through `RemoteActions`. Keep the agent bean free of AOP interceptor
bindings (`@Retryable`, `@Cacheable`, `@Transactional`, a custom `@Around`):
Micronaut generates a subclass that hides the annotations from the binder,
and such a bean fails at startup saying so. HTTP concerns such as
`@ExecuteOn` belong on the controller that delegates to the agent.

Every binding annotation of the core binds here: the 0.2.0 set
(`@SpaceTake`, `@SpaceNotify`, `@BidFunction`, `@Ballot`, `@OrderedTake`,
`@OnDecision`, `@SpaceRef`, `@CapabilityRef`, `@ProvidesCapability`) and the
0.3.0 set (`@SpaceJoin` with its `@Part`s, `@Propose`, `@OnEstimate`,
`@SpaceReduce`). A bean naming a space no group registers fails at startup
listing the spaces (including a join's part spaces, a proposal's vote space,
an estimate's result space, and a reduce's space) and the configured groups.
A plain bean whose only fleet annotation is `@SpaceReduce` enrolls like any
other (the core binder's bindings cover the reduce since 2026-10-09).
`core/src/test/.../AnnotationPassthroughTest` proves each of these through
Micronaut-managed beans, reading every outcome back from a space: the
`@SpaceNotify` fork (`Entries` of a `Tagged` entry, a `@SpaceRef` write), the
`@BidFunction` on an AUCTION space, `@Propose` with `@Ballot`, `@OnDecision`
and a `@CapabilityRef` client, a `Motion` return, a `Contribution` settled by
`@OnEstimate`, a `LEASED` `@SpaceJoin` over a `keyTag` part, `@SpaceReduce`
with `keyTag` and `accumulatorLease`, `@OrderedTake` over a two-peer ordered
log (a `@Singleton` registers the `OrderedTakes` coordinator before the clerk
enrolls; a one-member Raft log never elects a leader), and a
`@ProvidesCapability` bean advertised into the group.
`CompileTimeEnrollTest` covers the processor path for the 0.3.0 set.

A bean whose only fleet annotations are on methods (a plain `@Singleton`
with a `@SpaceNotify` or `@SpaceJoin` method) enrolls when the application
compiles with the processor module, which maps all ten method annotations
(`@SpaceTake`, `@SpaceNotify`, `@BidFunction`, `@Ballot`, `@OrderedTake`,
`@OnDecision`, `@SpaceJoin`, `@Propose`, `@OnEstimate`, `@SpaceReduce`) to
startup executables and also keeps the binder's reflection in native images:

```xml
<!-- maven-compiler-plugin annotationProcessorPaths, after micronaut-inject-java -->
<path>
  <groupId>ai.badmonkey.agentspaces</groupId>
  <artifactId>agentspaces-micronaut-processor</artifactId>
  <version>0.4.0</version>
</path>
```

```kotlin
// build.gradle.kts, under the Micronaut Gradle plugin
dependencies {
    annotationProcessor("ai.badmonkey.agentspaces:agentspaces-micronaut-processor:0.4.0")
}
```

```groovy
// build.gradle
dependencies {
    annotationProcessor 'ai.badmonkey.agentspaces:agentspaces-micronaut-processor:0.4.0'
}
```

## Assets

Every `AssetProvider` bean is served through the connector SDK on the data
space (`agentspaces.micronaut.assets.space`, default `data`): its AssetCards
publish and refresh, and `aspace:cap/data-query` requests are answered under
the profile's authorizer. `TableAssetProvider` works for in-memory tables; a
JDBC-backed provider over a Micronaut `DataSource` is the shape of a real
connector.

With `agentspaces.micronaut.service-asset.enabled=true` the embedded server
itself publishes an AssetCard once it has bound: asset
`micronaut.application.name`, the server's URI (or `external-uri`), shape
`http`, freshness `live`, and under `access` the scheme, instance id, and
whether it answers queries. `service-asset.query=get` with
`allowed-paths` lets a fleet agent with no network path to the service GET an
allowed path through the space; off by default. `ServiceAssetCustomizer`
beans adjust the card.

## The fleet as a service registry

`agentspaces.micronaut.registry.enabled=true` adds a `DiscoveryClient` over
the group's ad-cache: a service id resolves to every peer whose AssetCard
advertises an `http` asset of that name, from issuers the authorizer permits
`CONNECTOR_SERVE`. `@Client("orders")` then load-balances across the peers
that serve `orders` with no URL configured anywhere, and a lapsed card drops
its instance.

## Management, console, A2A

With `micronaut-management`: a readiness `HealthIndicator` named
`agentspaces` whose details are the fleet summary, an `InfoSource`, and the
`/agentspaces` endpoint (sensitive by default) with the summary and every
card the peer sees. With Micrometer: gauges `agentspaces.members`,
`agentspaces.cards.agents.local`, `agentspaces.cards.agents.remote`,
`agentspaces.cards.assets` per group, and `agentspaces.agents.enrolled`.

`agentspaces.console.enabled=true` serves the fleet console on
`agentspaces.console.port` with the application's `ConsoleViewCustomizer`,
`ConsoleCommand`, and `ConsolePanel` beans; `agentspaces.a2a.enabled=true`
serves the A2A gateway (with `agentspaces-a2a` on the classpath). Both keep
the Spring starter's rules, including the refusal to bind a routable address
without a token or OIDC gate.

## Properties this project adds

| Property | Default | Meaning |
| --- | --- | --- |
| `agentspaces.enabled` | `true` | Switch the whole integration off. |
| `agentspaces.micronaut.group` | first configured group | The group assets, the registry, and the views work in. |
| `agentspaces.micronaut.enroll.enabled` | `true` | Enroll annotated beans at startup. |
| `agentspaces.micronaut.assets.enabled` | `true` | Serve `AssetProvider` beans. |
| `agentspaces.micronaut.assets.space` | `data` | The data space; must be registered in the group when providers exist. |
| `agentspaces.micronaut.assets.freshness` | `PT5M` | Default freshness. |
| `agentspaces.micronaut.service-asset.enabled` | `false` | Publish the embedded server as an AssetCard. |
| `agentspaces.micronaut.service-asset.asset` | `micronaut.application.name` | The asset name, also the service id. |
| `agentspaces.micronaut.service-asset.external-uri` | the server's URI | What to advertise behind an ingress. |
| `agentspaces.micronaut.service-asset.description` | `Micronaut service <name>` | Card description. |
| `agentspaces.micronaut.service-asset.query` | `none` | `none` or `get`. |
| `agentspaces.micronaut.service-asset.allowed-paths` | empty | Path prefixes `get` may fetch. |
| `agentspaces.micronaut.service-asset.query-timeout` | `PT5S` | Per GET. |
| `agentspaces.micronaut.service-asset.max-body-bytes` | `1048576` | Body cap per GET. |
| `agentspaces.micronaut.registry.enabled` | `false` | Resolve `@Client` ids through fleet AssetCards. |
| `agentspaces.micronaut.management.enabled` | `true` | Health, info, endpoint, meters. |

The starter's own blocks bind unchanged through the shared wiring, `agentspaces.content.*`
included (`enabled`, `piece-size`, `escalate-above`, `store.backends`, `store.path`; SPEC v0.1.23
§8): with it on, `group.provider(Content.TYPE)` is the content swarm, a space entry above
`escalate-above` travels as an artifact, and the console gains the `content` panel. The LangChain4j
module's `capability-tools.content` adds the content tools to the model.

## How it is built

The library reuses the Spring starter's wiring without Spring. The published
`agentspaces-spring-boot-autoconfigure` jar carries no Spring classes (it
compiles against provided-scope stubs), and its properties POJO, authorizers,
and build methods have no Spring type in their signatures; the JVM ignores
the Spring annotations it cannot resolve. A Micronaut `@Factory` binds the
`agentspaces` subtree of the environment into that POJO and calls those
methods, so the transport, TLS, group, space, key-ring, and capability wiring
lives in one place for both frameworks. The suite runs with no Spring on the
classpath and pins that assumption.

Jackson: Micronaut 5 is on Jackson 3; the core is on Jackson 2.21.7, with
`jackson-annotations` 2.22 for Micronaut. Transport: TCP and TLS;
`agentspaces-transport-quic` pins Netty 4.1 and Micronaut manages 4.2, so
QUIC is not supported here.

## LangChain4j and MCP

Two more modules put models on the fleet. `agentspaces-micronaut-langchain4j`
(its README in `langchain4j/`) gives every `@AiService` the fleet's agents as
tools, renews a worker's take lease per model round-trip, keeps chat memory
in a replicated space, serves and consumes models as a fleet service
(interoperable with the Spring AI module through the shared
`agentspaces-model-wire` records), ranks semantic discovery with a real
embedding model, and sums token usage across the fleet. The fleet tool
catalogue both bridges share is in this library, under
`agentspaces.micronaut.tools.*` (`prefix`, `include-agents`,
`exclude-agents`, `require-attested`, `timeout`, `on-timeout`,
`refresh-interval`, `max`). The same provider carries the capability tools
(a model opens a vote, casts a ballot, reads a tally or a decision,
contributes to a push-sum, reads its estimate) when
`agentspaces.micronaut.langchain4j.capability-tools.vote` or `.aggregate`
is on, and the discovery tools under `discovery-tools.*`.
`agentspaces-micronaut-mcp` (README in `mcp/`) publishes the fleet's tools
on micronaut-mcp's server, exports this peer's capability and discovery
tools the same way under `agentspaces.micronaut.mcp.export.capability-tools.*`
and `export.discovery-tools.*`, and republishes external MCP servers' tools
as fleet actions.

## Artifacts

| Artifact | What it is |
| --- | --- |
| `ai.badmonkey.agentspaces:agentspaces-micronaut` | the library: the peer, agents, assets, registry, management, console, A2A |
| `ai.badmonkey.agentspaces:agentspaces-micronaut-processor` | annotation-processor path only: compile-time enrollment and native-image hints |
| `ai.badmonkey.agentspaces:agentspaces-micronaut-langchain4j` | the LangChain4j bridge |
| `ai.badmonkey.agentspaces:agentspaces-micronaut-mcp` | the MCP bridge |

All at version 0.4.0 on Maven Central under `ai.badmonkey.agentspaces`, on
the AgentSpaces core 0.4.0 and
[agentspaces-model-wire](https://github.com/badmonkeyai/agentspaces-model-wire)
0.4.0, which they bring in transitively. Add any of them as the library above
is added, with `implementation` and an explicit version. Every jar carries the licence and notice under `META-INF`; releases
ship sources and javadoc jars, signed. `mvn clean verify` builds and tests
the libraries on JDK 25; `-Pexamples` adds the examples; `-Pcentral-publish`
attaches sources and javadoc, signs, and deploys through the Central Portal
(`mvn -Pcentral-publish clean deploy`, with the `central` server id and
`MAVEN_GPG_PASSPHRASE`).

## Layout

- `core/`: the library, `agentspaces-micronaut`.
- `processor/`: `agentspaces-micronaut-processor`, annotation-processor path only.
- `langchain4j/`: `agentspaces-micronaut-langchain4j`.
- `mcp/`: `agentspaces-micronaut-mcp`.
- `examples/micronaut-fleet/` (example 16, under `-Pexamples`): an orders
  service that is agent and asset, and an analyst that drives it through the
  task space, the data space, and `@Client` through the registry.
- `examples/micronaut-ai-fleet/` (example 17): a LangChain4j orchestrator
  with no provider key, two workers that are its tools and its model server,
  and an MCP gateway peer.

`mvn clean install` builds and tests the library; `-Pexamples` adds the
example.

## The runtime underneath

Since 2026-10-08 the base library depends on
[`agentspaces-runtime`](https://github.com/badmonkeyai/agentspaces-langchain4j)
for `FleetContext`, `FleetParticipant`, the properties binding, and the tool
catalogue (`FleetToolCatalog`, `FleetAction`, `FleetTool`, `RecordSchemas`,
`ToolInvocation`, `FleetToolsChangedEvent`, in `ai.badmonkey.agentspaces.tools`);
`FleetActions` is the catalogue as a bean, forwarding its change events to
Micronaut's publisher, and `FleetScheduler` is Micronaut's scheduled
executor as the runtime's scheduler. `ai.badmonkey.agentspaces.micronaut.FleetContext`
and `FleetParticipant` are subtypes of the runtime's, so beans keep their
names; `FleetAction`, `FleetToolFilter`, and `ToolNamingStrategy` in the
`tools` package are deprecated aliases for one release. The LangChain4j
bridge is wiring over `agentspaces-langchain4j` in the same way.
