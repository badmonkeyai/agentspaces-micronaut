# Management, console, A2A

Written for: Micronaut developers who build with agentspaces-micronaut.

With `micronaut-management`: a readiness `HealthIndicator` named
`agentspaces` whose details are the fleet summary, an `InfoSource`, and the
`/agentspaces` endpoint (sensitive by default) with the summary and every
card the peer sees. With Micrometer: gauges `agentspaces.members`,
`agentspaces.cards.agents.local`, `agentspaces.cards.agents.remote`,
`agentspaces.cards.assets` per group, and `agentspaces.agents.enrolled`.

**Loop liveness.** A liveness `HealthIndicator` named `agentspaces-loop`
(`/health/liveness`) reports the node loop's health as its watchdog judges
it: UP for OK and DEGRADED, DOWN once the watchdog escalated, with the state
and reasons in the details. It is the counterpart of the Spring starter's
`LoopLiveness`, so a supervisor that probes liveness restarts a peer whose
loop broke. A host with no probe sets `agentspaces.watchdog.escalation: exit`
instead. `agentspaces.micronaut.management.enabled=false` removes it with the
rest.

**Timing.** The content store and the persistent trees run their own
deadlines on the node loop (`joinLoop`), and the card refresh is a one-shot on
the node's time source, re-armed after each run. Nothing here wakes the node
every `tick-millis`; since 0.7.0 that key only sets the deprecated onTick
adapters' cadence. An application `TimeSource` bean (a `VirtualTime` in a
test) is the node's clock too.

`agentspaces.console.enabled=true` serves the fleet console on
`agentspaces.console.port` with the application's `ConsoleViewCustomizer`,
`ConsoleCommand`, and `ConsolePanel` beans; `agentspaces.a2a.enabled=true`
serves the A2A gateway (with `agentspaces-a2a` on the classpath). Both keep
the Spring starter's rules, including the refusal to bind a routable address
without a token or OIDC gate.
