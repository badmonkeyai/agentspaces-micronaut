# agentspaces-micronaut documents

Written for: anyone who read the README and wants the rest of the story.

The README is short by design: what this is, the quick start, and how to add it to a build. Everything else lives here, one topic per document.

| Document | Written for | What it holds |
|---|---|---|
| [About agentspaces-micronaut](about.md) | Micronaut developers who build with agentspaces-micronaut | What AgentSpaces is, and what this project adds to it. |
| [How to use AI with AgentSpaces](how-to-use-ai.md) | Micronaut developers who build with agentspaces-micronaut | Tested walk-throughs that build an agent, hand it work from another peer, and survive a crash. |
| [Agents](agents.md) | Micronaut developers who build with agentspaces-micronaut | `@SpaceAgent` is a bean-defining stereotype (a singleton) that is also an `@AgentSpec`. |
| [Assets](assets.md) | Micronaut developers who build with agentspaces-micronaut | Every `AssetProvider` bean is served through the connector SDK on the data space (`agentspaces.micronaut.assets.space`. |
| [The fleet as a service registry](service-registry.md) | Micronaut developers who build with agentspaces-micronaut | `agentspaces.micronaut.registry.enabled=true` adds a `DiscoveryClient` over the group's ad-cache: a service id resolves to every peer whose AssetCard. |
| [Management, console, A2A](management.md) | Micronaut developers who build with agentspaces-micronaut | With `micronaut-management`: a readiness `HealthIndicator` named `agentspaces` whose details are the fleet summary, an `InfoSource`. |
| [Properties this project adds](properties.md) | Micronaut developers who build with agentspaces-micronaut | The starter's own blocks bind unchanged through the shared wiring, `agentspaces.content.*` included (`enabled`, `piece-size`, `escalate-above`. |
| [How it is built](how-it-is-built.md) | Micronaut developers who build with agentspaces-micronaut | The library reuses the Spring starter's wiring without Spring. |
| [LangChain4j and MCP](langchain4j-and-mcp.md) | Micronaut developers who build with agentspaces-micronaut | Two more modules put models on the fleet. |
| [Artifacts](artifacts.md) | Micronaut developers who build with agentspaces-micronaut | All at version 0.6.0 on Maven Central under `ai.badmonkey.agentspaces`, on the AgentSpaces core 0.6.0 and agentspaces-model-wire 0.6.0. |
| [Layout](layout.md) | Micronaut developers who build with agentspaces-micronaut | `mvn clean install` builds and tests the library; `-Pexamples` adds the example. |
| [The runtime underneath](the-runtime-underneath.md) | Micronaut developers who build with agentspaces-micronaut | Since 2026-10-08 the base library depends on `agentspaces-runtime` for `FleetContext`, `FleetParticipant`, the properties binding. |
