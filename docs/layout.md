# Layout

Written for: Micronaut developers who build with agentspaces-micronaut.

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
