# LangChain4j and MCP

Written for: Micronaut developers who build with agentspaces-micronaut.

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
