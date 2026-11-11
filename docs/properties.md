# Properties this project adds

Written for: Micronaut developers who build with agentspaces-micronaut.

| Property | Default | Meaning |
| --- | --- | --- |
| `agentspaces.enabled` | `true` | Switch the whole integration off. |
| `agentspaces.micronaut.group` | first configured group | The group assets, the registry, and the views work in. |
| `agentspaces.micronaut.enroll.enabled` | `true` | Enroll annotated beans at startup. |
| `agentspaces.micronaut.assets.enabled` | `true` | Serve `AssetProvider` beans. |
| `agentspaces.micronaut.assets.space` | `data` | The data space; must be registered in the group when providers exist. |
| `agentspaces.micronaut.assets.freshness` | `PT5M` | Default freshness. |
| `agentspaces.micronaut.service-asset.enabled` | `false` | Publish the embedded server as an AssetCard. |
| `agentspaces.micronaut.service-asset.asset` | `micronaut.application.name` | The asset name, also the service id. |
| `agentspaces.micronaut.service-asset.external-uri` | the server's URI | What to advertise behind an ingress. |
| `agentspaces.micronaut.service-asset.description` | `Micronaut service <name>` | Card description. |
| `agentspaces.micronaut.service-asset.query` | `none` | `none` or `get`. |
| `agentspaces.micronaut.service-asset.allowed-paths` | empty | Path prefixes `get` may fetch. |
| `agentspaces.micronaut.service-asset.query-timeout` | `PT5S` | Per GET. |
| `agentspaces.micronaut.service-asset.max-body-bytes` | `1048576` | Body cap per GET. |
| `agentspaces.micronaut.registry.enabled` | `false` | Resolve `@Client` ids through fleet AssetCards. |
| `agentspaces.micronaut.management.enabled` | `true` | Health, info, endpoint, meters. |

The starter's own blocks bind unchanged through the shared wiring, `agentspaces.content.*`
included (`enabled`, `piece-size`, `escalate-above`, `store.backends`, `store.path`; SPEC v0.1.23
§8): with it on, `group.provider(Content.TYPE)` is the content swarm, a space entry above
`escalate-above` travels as an artifact, and the console gains the `content` panel. The LangChain4j
module's `capability-tools.content` adds the content tools to the model.

Persistent data trees (SPEC annex H) bind from `agentspaces.pdt.*` with the keys
and defaults the Spring starter reads: `enabled`, `inline-delta` (`32KB`), `load-timeout` (`10s`),
`follow` (`full` or `lazy`), `sealing`, `head-lease`, `cancel-grace`, `collect-every`,
`store.kind` (`pack`, `memory`, or `bean`), `store.path`, `store.quota`, `store.require`,
`retain.versions`, `retain.age`, `take.lease`, `take.timeout`, `take.retries`, `refs.<name>.mode`
(`multi` or `linear`), `refs.<name>.follow` (`full` or `lazy`, the ref's own follow), and
`spaces.<name>.escalate-above` and `spaces.<name>.require`. This project adds
`agentspaces.pdt.space` (`refs`): the registered space of each group that holds the ref heads, so
the space's own admission decides who writes one. With it on, a `Pdt` per group lives in the
`Pdts` bean and the fleet group's is the `Pdt` bean; a `NodeStore` bean named for a group, or one
unnamed bean for the fleet group, replaces the configured store.

The shared wiring builds each group's content swarm before the factory builds the facade, so
`PdtStores` hands the wiring the swarm's tree store: the bridge over the group's node store,
sealed under the group's content key when `sealing` is `sealed-nodes`, with `store.quota` applied
to every node the swarm receives (a fetch past the quota fails with the refusal). The spaces
under `spaces.*` escalate through a deferred seam in the shared holder, which reaches the facade
once the factory adopts it; before then a write falls back to the flat swarm and a read retries.
A bean's `@PdtRef("name")` field or verb parameter gets the group facade's ref for its declared
type, such as `Ref<PMap<String, Fact>>`.
