# Management, console, A2A

Written for: Micronaut developers who build with agentspaces-micronaut.

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
