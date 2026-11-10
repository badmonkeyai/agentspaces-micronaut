# Assets

Written for: Micronaut developers who build with agentspaces-micronaut.

Every `AssetProvider` bean is served through the connector SDK on the data
space (`agentspaces.micronaut.assets.space`, default `data`): its AssetCards
publish and refresh, and `aspace:cap/data-query` requests are answered under
the profile's authorizer. `TableAssetProvider` works for in-memory tables; a
JDBC-backed provider over a Micronaut `DataSource` is the shape of a real
connector.

With `agentspaces.micronaut.service-asset.enabled=true` the embedded server
itself publishes an AssetCard once it has bound: asset
`micronaut.application.name`, the server's URI (or `external-uri`), shape
`http`, freshness `live`, and under `access` the scheme, instance id, and
whether it answers queries. `service-asset.query=get` with
`allowed-paths` lets a fleet agent with no network path to the service GET an
allowed path through the space; off by default. `ServiceAssetCustomizer`
beans adjust the card.
