# Artifacts

Written for: Micronaut developers who build with agentspaces-micronaut.

| Artifact | What it is |
| --- | --- |
| `ai.badmonkey.agentspaces:agentspaces-micronaut` | the library: the peer, agents, assets, registry, management, console, A2A |
| `ai.badmonkey.agentspaces:agentspaces-micronaut-processor` | annotation-processor path only: compile-time enrollment and native-image hints |
| `ai.badmonkey.agentspaces:agentspaces-micronaut-langchain4j` | the LangChain4j bridge |
| `ai.badmonkey.agentspaces:agentspaces-micronaut-mcp` | the MCP bridge |

All at version 0.6.0 on Maven Central under `ai.badmonkey.agentspaces`, on
the AgentSpaces core 0.6.0 and
[agentspaces-model-wire](https://github.com/badmonkeyai/agentspaces-model-wire)
0.6.0, which they bring in transitively. Add any of them as the library above
is added, with `implementation` and an explicit version. Every jar carries the licence and notice under `META-INF`; releases
ship sources and javadoc jars, signed. `mvn clean verify` builds and tests
the libraries on JDK 25; `-Pexamples` adds the examples; `-Pcentral-publish`
attaches sources and javadoc, signs, and deploys through the Central Portal
(`mvn -Pcentral-publish clean deploy`, with the `central` server id and
`MAVEN_GPG_PASSPHRASE`).
