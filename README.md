# agentspaces-micronaut

**AgentSpaces for Micronaut microservices**, from Bad Monkey AI. Add the library and the `agentspaces.*` properties, and the service is a peer of a peer-to-peer fleet: its annotated beans work the fleet's spaces, it publishes cards for its agents and its own HTTP presence, and other services resolve it through `@Client`.

## Quick start: build your first agent with Micronaut

An agent is a method. Its parameter is the work it takes, and its return value is the next entry in the space. In Micronaut the method sits on a plain `@Singleton`: the space hands it one `Order` at a time under a lease, and the returned `Shipment` completes the order. The `agentspaces-micronaut-processor` annotation processor enrolls the bean at compile time, so the one fleet annotation on the method is all the bean needs.

**1. Add the dependency and the processor.** Micronaut 5 compiles to Java 25 bytecode, so the application runs on JDK 25.

```xml
<dependency>
  <groupId>ai.badmonkey.agentspaces</groupId>
  <artifactId>agentspaces-micronaut</artifactId>
  <version>0.6.0</version>
</dependency>
```

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths combine.children="append">
      <path>
        <groupId>ai.badmonkey.agentspaces</groupId>
        <artifactId>agentspaces-micronaut-processor</artifactId>
        <version>0.6.0</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

**2. Configure the peer.** These are the Spring Boot starter's `agentspaces.*` properties, unchanged. The default `mtls` security profile secures every link, so the file has no security block. The `${shop.*}` placeholders let you start a second instance from the command line.

```yaml
micronaut:
  application:
    name: shop

agentspaces:
  bind: 127.0.0.1:${shop.port:7701}
  groups:
    - name: shop
      founding: shop-v1
      seeds: ${shop.seeds:}      # empty on the first instance
      spaces:
        - name: orders
```

**3. Write the agent.** Two records name the entries, and the bean takes one `Order` and returns a `Shipment`. `main` starts the application and, when you pass an order, writes it and prints the shipment. The listings omit imports.

```java
public record Order(String orderId, String item) {
}

public record Shipment(String orderId, String item, String by) {
}
```

```java
@Singleton
public class Fulfiller {

    @SpaceTake(lease = "30s")
    public Shipment ship(Order order) {
        return new Shipment(order.orderId(), order.item(), "fulfiller");
    }
}
```

```java
public class ShopApp {

    public static void main(String[] args) throws InterruptedException {
        ApplicationContext context = Micronaut.run(ShopApp.class, args);
        String item = context.getProperty("shop.order", String.class).orElse("");
        if (item.isBlank()) {
            Thread.currentThread().join();               // an agent instance: serve until killed
        } else {                                         // a desk instance: write one order, read the shipment
            Space orders = context.getBean(AgentSpaces.class).group("shop").space("orders");
            orders.write(new Order("o-1", item), Lease.of(Duration.ofMinutes(10)));
            orders.read(Template.of(Shipment.class), Duration.ofSeconds(30)).ifPresent(System.out::println);
            context.close();
        }
    }
}
```

**4. Run two instances.** Start the first instance, then a second one that joins through it and writes an order.

```
mvn -q compile exec:java -Dexec.mainClass=shop.ShopApp
mvn -q compile exec:java -Dexec.mainClass=shop.ShopApp -Dexec.args="--shop.port=7702 --shop.seeds=127.0.0.1:7701 --shop.order=kite"
```

The second instance prints the shipment. Either instance can take the order, because both run the same agent.

**5. Kill one.** Add `System.out.println("shipping " + order.orderId());` and `Thread.sleep(10_000);` at the top of `ship` (the method then needs `throws InterruptedException`). Start both instances, write an order from the second, and run `kill -9` on whichever instance prints `shipping`. If you killed the first instance, the second prints the shipment. If you killed the second, the first prints `shipping o-1` again when the 30 second lease lapses.

The [documents](docs/README.md) add the rest: agents and their return conventions, assets and the fleet as a service registry, management endpoints, and the LangChain4j and MCP bridges.

## What it is

- A Micronaut 5 library that reads the same `agentspaces.*` properties and annotations as the Spring Boot starter.
- An annotation processor for compile-time enrollment and native-image hints.
- The fleet as a service registry, health and management endpoints, the console, and A2A.
- LangChain4j three ways, and MCP export and import.
- The library is Java 21 bytecode; Micronaut 5.2 runs on JDK 25.

## Add it to your build

The quick start's first step is the build: `ai.badmonkey.agentspaces:agentspaces-micronaut:0.6.0` from Maven Central, with the optional processor `agentspaces-micronaut-processor`. [Artifacts](docs/artifacts.md) lists every module and [Properties this project adds](docs/properties.md) the keys.

## Where to read next

1. **[The developer guide](https://www.badmonkey.ai/agentspaces/developer-guide.html#micronaut)**: its Micronaut chapter covers agents, assets, the registry, management, properties, and deployment recipes.
2. **[The examples](examples/)**: examples 16 and 17 run a Micronaut fleet and its LangChain4j services.
3. **[The documents](docs/README.md)**: everything this README leaves out, one topic per document, each with the reader it is written for.

## License

Apache License 2.0, Copyright 2026 Bad Monkey, Inc. See [LICENSE](LICENSE) and [NOTICE](NOTICE). Contributions are welcome under the Developer Certificate of Origin; see [CONTRIBUTING.md](CONTRIBUTING.md). Report a vulnerability privately, as [SECURITY.md](SECURITY.md) describes.
