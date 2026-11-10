# The runtime underneath

Written for: Micronaut developers who build with agentspaces-micronaut.

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
