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

The replication settings bind the same way, from the starter's two strict blocks
(srad/replication-by-entry.md appendix D). Most applications leave them out:

| Property | Default | Meaning |
| --- | --- | --- |
| `agentspaces.gossip.audit` | `true` | Advertise `wire.ae=2`. Toward a member that advertises it too, compare a space by its fingerprinted trie and fetch only the entries that differ (SPEC annex I.6). |
| `agentspaces.gossip.audit-sessions` | `4` | Audit sessions per asker, 1 to 4. |
| `agentspaces.gossip.audit-budget` | `1048576` | The bytes each audit answer may hold, 4096 to 4194304. |
| `agentspaces.gossip.probe-hash-streams` | `32` | How many streams a probe hashes one by one, 0 to 256. |
| `agentspaces.space.horizon` | `per-entry` | Advertise `wire.horizon=1`: once every member of a space's view does, a withdrawn entry is forgotten by its own lease (SPEC §7.3). `legacy` keeps 2 x maxLease; set it on a peer that renews for an identity another peer renews at the same time. |
| `agentspaces.space.renew-shortening-log` | `true` | Log, once per space, the first renewal that asks for less than its lease has left. |

An unknown key under either block stops the application at start and names the keys the block
takes, so a misspelt setting never passes silently. The environment form works too:
`AGENTSPACES_GOSSIP_AUDIT_BUDGET` binds `agentspaces.gossip.audit-budget`, and the other spellings
Micronaut makes of the variable are dropped before the check.

A renewal never shortens a lease (SPEC §7.2). `EntryHandle.renew`, a take's `renew`, and a
subscription's `renew` extend to at least the given time from now, so a renewal for less than the
lease has left changes nothing. To end an entry sooner, cancel it, and write it again with the
shorter lease if it should stay.

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
