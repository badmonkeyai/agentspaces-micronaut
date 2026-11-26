# agentspaces-micronaut changelog

What changed in the Micronaut integration, newest first. The Java core's history is in `docs/dvit/CHANGELOG.md` of the AgentSpaces workspace.

## Unreleased: 0.7.0

Work since the 0.6.0 release, for 0.7.0. The build reads 0.7.0-SNAPSHOT; nothing is published.

- **A loop liveness indicator.** `LoopLivenessIndicator` (named `agentspaces-loop`) serves `/health/liveness`. It is UP while the node's loop is OK or DEGRADED, and DOWN once the watchdog escalates. `agentspaces.micronaut.management.enabled=false` removes it (`docs/management.md`).
- **On the node's loop.** An application `TimeSource` bean is the node's clock. Card refresh is a one-shot on the node's time source, and the fleet scheduler, content stores and PDT run on the node's loop. `tick-millis` now paces only the deprecated `onTick` adapters.
- **Configuration.** Dotted `agentspaces.ttl.expert-overrides` keys bind from every property source. Asset connectors honour `agentspaces.watchdog.connector-hold-limit`. The replication keys `agentspaces.gossip.audit`, `audit-sessions`, `audit-budget`, `probe-hash-streams`, `agentspaces.space.horizon` and `renew-shortening-log` are documented in `docs/properties.md`.
- **PDT.** At the default store path each peer gets its own directory, `<path>/<group>/<peerId>`.
