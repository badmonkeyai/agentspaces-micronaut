# Example 17: Micronaut AI Fleet

Three Micronaut peers on one AgentSpaces fleet. The **worker** runs two
agents, a summarizer that uses the peer's own LangChain4j model and a pricer
that needs none, and serves that model to the fleet. The **orchestrator** is
a LangChain4j `@AiService` with no provider key: its tools are the workers
(live, from their AgentCards) and its model is the fleet (calls travel
through the space to the worker). The **gateway** publishes the fleet's tools
on an MCP server over HTTP, for any MCP client.

## What it demonstrates

- `FleetToolProvider` applied to an `@AiService` automatically: the model
  sees `fleet_summarizer_Brief` and `fleet_pricer_PriceRequest`.
- `model-server.enabled` on the worker and `model-client.enabled` on the
  orchestrator: `FleetChatModel` is the orchestrator's `ChatModel` bean.
- `@SpaceTake(lease = "2m")` on a worker that calls a model, with
  `TakeLeaseListener` renewing the lease per round-trip.
- `usage.enabled`: token usage per model and agent on the worker.
- `mcp.export.enabled` on the gateway: the two fleet tools at
  `http://localhost:8090/mcp`.

## The API in this example

| Where | What |
| --- | --- |
| `Workers.Summarizer`, `Workers.Pricer` | `@SpaceAgent` + `@SpaceTake` in the take space `tasks` |
| `Orchestrator` | `@AiService` with a `@SystemMessage`, nothing fleet-specific |
| `application-worker.yml` | `model-server`, `usage`, and the OpenAI model (`OPENAI_API_KEY`) |
| `application-orchestrator.yml` | `model-client` with `model: gpt-4.1-mini` and no key |
| `application-gateway.yml` | `micronaut.mcp.server` over HTTP and `mcp.export` |

## Running it

From `agentspaces-micronaut/` with the libraries installed (`mvn install`),
and `OPENAI_API_KEY` set for the worker:

```sh
cd examples/micronaut-ai-fleet
mvn -q compile exec:java -Dexec.mainClass=ai.badmonkey.agentspaces.examples.micronaut.ai.WorkerApp
# in two more terminals
mvn -q compile exec:java -Dexec.mainClass=ai.badmonkey.agentspaces.examples.micronaut.ai.GatewayApp
mvn -q compile exec:java -Dexec.mainClass=ai.badmonkey.agentspaces.examples.micronaut.ai.OrchestratorApp
```

The orchestrator prints its plan and exits. List the gateway's tools:

```sh
curl -s -H 'content-type: application/json' -H 'accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' localhost:8090/mcp
```

The test, `MicronautAiFleetTest`, boots the three peers in one JVM over TCP
on free ports with a scripted model on the worker and asserts the loop: the
orchestrator's plan comes from both fleet tools, its three model round-trips
were served through the space, usage was recorded, and the gateway published
both tools.

## Next steps

- Point `model-client.model` at a model a Spring AI peer serves (example 15):
  the wire is the same.
- Turn on `memory.enabled` and give the summarizer a conversation that
  survives a restart.
- Add `micronaut.mcp.client.*` to the gateway and `mcp.import.enabled`, so an
  external MCP server's tools join the fleet.
