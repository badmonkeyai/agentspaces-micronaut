# agentspaces-micronaut-mcp

The fleet and MCP on Micronaut 5.2, through `micronaut-mcp` 2.1.

```xml
<dependency>
  <groupId>ai.badmonkey.agentspaces</groupId>
  <artifactId>agentspaces-micronaut-mcp</artifactId>
  <version>0.3.0</version>
</dependency>
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("ai.badmonkey.agentspaces:agentspaces-micronaut-mcp:0.3.0")
}
```

```groovy
// build.gradle
dependencies {
    implementation 'ai.badmonkey.agentspaces:agentspaces-micronaut-mcp:0.3.0'
}
```

## Export: the fleet's tools on an MCP server

With `micronaut-mcp-server-java-sdk` and `micronaut.mcp.server.transport`
set, `agentspaces.micronaut.mcp.export.enabled=true` publishes the fleet's
tools (the base library's catalogue, `agentspaces.micronaut.tools.*`) on the
server and keeps them in step with the cards: a tool is added when a card
appears and removed when it lapses. The export has its own `include-agents`
and `exclude-agents`, because an MCP client sits outside the fleet's trust
boundary. Over STDIO the server notifies clients of changes when
`micronaut.mcp.server.tools.list-changed=true`; over HTTP micronaut-mcp is
stateless, and clients see the change on their next `tools/list`. The bridge
makes the server advertise the tools capability even when it has no tools of
its own at startup, since the fleet's arrive later.

```yaml
micronaut:
  mcp:
    server: { transport: HTTP, info: { name: fleet, version: 0.3.0 } }
agentspaces:
  micronaut:
    mcp:
      export: { enabled: true, include-agents: [summarizer, pricer] }
```

The export also publishes this peer's own tools from
`agentspaces-langchain4j` (an optional dependency here), each as an MCP tool
with the `@Tool` method's input schema. `export.capability-tools.vote`
exports `propose_vote`, `cast_ballot`, `read_tally`, and `read_decision`;
`export.capability-tools.aggregate` exports `contribute` and
`read_estimate`; `export.discovery-tools.agents`, `.assets`, and `.data`
export `find_fleet_agents`, `list_fleet_assets`, and `fetch_fleet_data`.
All are off by default. An MCP client calling them acts as this peer's
agent: its ballot is that agent's attested record and its contribution
joins the group's epoch, so switch them on only for a client that may act
in this peer's name. These tools are added on start and removed on stop;
only the catalogue's remote actions change while the peer runs.

```yaml
agentspaces:
  micronaut:
    mcp:
      export:
        enabled: true
        capability-tools: { vote: true, aggregate: true }
        discovery-tools: { agents: true }
```

## Import: an MCP server's tools as a fleet agent

With `micronaut-mcp-client-langchain4j` and `micronaut.mcp.client.*`
configured, `agentspaces.micronaut.mcp.import.enabled=true` publishes one
AgentCard per MCP client (agent `mcp-<name>`, the name being the client's
configuration key), one `take` action per tool, consuming `McpToolCall(server,
tool, argumentsJson, callId)` and producing `McpToolResult`. A worker loop
takes calls for that server from `import.space`, runs the tool through the
client, and completes the take with the result. Any peer on the fleet,
Spring, Micronaut, Python, or TypeScript, invokes the external server's tool
through the space; only the importing peer holds the connection and
credentials. A server that is not up at startup is retried every five
seconds, and tools are re-listed on each card refresh. `import.servers`
limits which clients are imported; restrict who may write into the import
space (`admission: authorizer` with `space-write`).

```yaml
micronaut:
  mcp:
    client:
      transport: HTTP
      http:
        search: { url: https://mcp.example/mcp }
agentspaces:
  groups:
    - name: fleet
      spaces: [ { name: mcp-calls } ]
  micronaut:
    mcp:
      import: { enabled: true, servers: [search] }
```

## Properties

| Property | Default |
| --- | --- |
| `agentspaces.micronaut.mcp.export.enabled` / `include-agents` / `exclude-agents` | `false` / all / none |
| `agentspaces.micronaut.mcp.export.capability-tools.vote` / `aggregate` / `ballot-lease` / `settle-timeout` | `false` / `false` / `1h` / `10s` |
| `agentspaces.micronaut.mcp.export.discovery-tools.agents` / `assets` / `data` / `data-space` / `timeout` / `max-result-chars` | `false` ×3 / `data` / `10s` / `8000` |
| `agentspaces.micronaut.mcp.import.enabled` / `space` / `lease` / `result-lease` / `servers` | `false` / `mcp-calls` / `5m` / `10m` / all |
