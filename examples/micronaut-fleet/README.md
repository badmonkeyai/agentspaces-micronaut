# Example 16: Micronaut Fleet

Two Micronaut microservices on one AgentSpaces fleet. The **orders service**
is both an agent and a data source: one pricing method serves `POST
/orders/price` and takes `PriceRequest` entries from the fleet's task space,
its orders table is an AssetCard, and its HTTP presence is an AssetCard. The
**analyst** has no server of its own and drives the orders service three
ways: through the task space, through the data space, and over HTTP with
`@Client("orders")` resolved through the fleet.

## What it demonstrates

- `@SpaceAgent` on a plain bean (`Pricer`) with a `@SpaceTake` method, and
  a `@Controller` that calls the same method for HTTP. The service's
  AgentCard lists `price` as an action.
- A plain `@Singleton` with only a `@SpaceNotify` method
  (`FindingsWatcher`) enrolling through `agentspaces-micronaut-processor`.
- An `AssetProvider` bean (`OrdersAssets`) served through the connector SDK,
  read by the analyst with `DataQueryClient`.
- `agentspaces.micronaut.service-asset.enabled`: the embedded server as an
  AssetCard, and `agentspaces.micronaut.registry.enabled`: `@Client("orders")`
  resolving to it with no URL configured anywhere.
- `/health`, `/info`, and `/agentspaces` on the orders service.

## The API in this example

| Where | What |
| --- | --- |
| `orders/Pricer` | `@SpaceAgent` + `@SpaceTake(space = "tasks", resultSpace = "findings")` |
| `orders/OrdersController` | `@Controller("/orders")`, `@ExecuteOn(BLOCKING)`, delegates to `Pricer` |
| `orders/OrdersAssets` | a `TableAssetProvider` bean named `orders-table` |
| `analyst/FindingsWatcher` | `@SpaceNotify(space = "findings")` on a plain singleton |
| `analyst/OrdersClient` | `@Client("orders")`, resolved by `FleetDiscoveryClient` |
| `analyst/AnalystRun` | the three interactions: write a task, fetch the asset, call the client |
| `application.yml`, `application-orders.yml`, `application-analyst.yml` | the shared fleet, and each service's bind, seeds, and switches |

## Running it

From `agentspaces-micronaut/` with the library installed (`mvn install`):

```sh
cd examples/micronaut-fleet
mvn -q compile exec:java -Dexec.mainClass=ai.badmonkey.agentspaces.examples.micronaut.orders.OrdersService
# in another terminal
mvn -q compile exec:java -Dexec.mainClass=ai.badmonkey.agentspaces.examples.micronaut.analyst.Analyst
```

The orders service binds the fleet on `127.0.0.1:7500` and HTTP on `8080`;
the analyst seeds from it. Then:

```sh
curl -s localhost:8080/orders/2
curl -s -XPOST -H 'content-type: application/json' -d '{"orderId":"1"}' localhost:8080/orders/price
curl -s localhost:8080/agentspaces | head -c 600
```

The test, `MicronautFleetTest`, boots both services in one JVM over TCP on
free ports and asserts the whole loop: the request written into `tasks` is
priced into `findings` and seen by the watcher, the orders table reads
through the data space, and the client reaches the service through the
registry.

## Next steps

- Replace `OrdersAssets` with a provider over a Micronaut `DataSource`.
- Turn on the console (`agentspaces.console.enabled`) on the orders service
  and watch the spaces.
- Add a second orders instance and watch `@Client("orders")` balance across
  both cards.
