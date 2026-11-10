# The fleet as a service registry

Written for: Micronaut developers who build with agentspaces-micronaut.

`agentspaces.micronaut.registry.enabled=true` adds a `DiscoveryClient` over
the group's ad-cache: a service id resolves to every peer whose AssetCard
advertises an `http` asset of that name, from issuers the authorizer permits
`CONNECTOR_SERVE`. `@Client("orders")` then load-balances across the peers
that serve `orders` with no URL configured anywhere, and a lapsed card drops
its instance.
