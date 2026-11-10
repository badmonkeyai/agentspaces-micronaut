# How to use AI with AgentSpaces

Written for: Micronaut developers who build with agentspaces-micronaut.

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

## 1. Build an agent and hand it work from another service

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

## 2. A model shared across peers, with no key on the orchestrator

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

## 3. The service itself as an asset card

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
