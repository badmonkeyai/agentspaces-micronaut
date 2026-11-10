# About agentspaces-micronaut

Written for: Micronaut developers who build with agentspaces-micronaut.

**A peer-to-peer coordination fabric for agents, services, and the people who work with them.**

AgentSpaces is a P2P framework to quickly discover and join P2P networks, where agents, software services, and people share assets, queries, results, and coordinate. The framework helps you build fleets of agents, tie in other software, and coordinate across them with a replicated tuple space, quorum building, and more. Agents post work, claim tasks, publish results, vote on decisions, and recover from each other's failures. There is no broker, no registry, no scheduler, and no server to keep alive. Coordination is peer-to-peer, secured end to end, and replicated across the fleet.

## About agentspaces-micronaut

AgentSpaces for Micronaut microservices. Add the library and the
`agentspaces.*` properties the Spring Boot starter reads, and the service is a
peer: its annotated beans work the fleet's spaces and publish AgentCards, its
`AssetProvider` beans and its own HTTP presence publish AssetCards, other
services resolve it through `@Client`, and its health and management
endpoints show the fleet. A standalone project on the published AgentSpaces
0.6.0 libraries and Micronaut 5.2. The library is Java 21 bytecode; Micronaut
5.2 itself is Java 25 bytecode, so applications run on JDK 25.

The library is on Maven Central under the group `ai.badmonkey.agentspaces`;
no repository configuration is needed. It releases from this repository on
its own cadence, so it carries its version explicitly.

Maven (`pom.xml`):

```xml
<dependency>
  <groupId>ai.badmonkey.agentspaces</groupId>
  <artifactId>agentspaces-micronaut</artifactId>
  <version>0.6.0</version>
</dependency>
```

Gradle Kotlin DSL (`build.gradle.kts`):

```kotlin
dependencies {
    implementation("ai.badmonkey.agentspaces:agentspaces-micronaut:0.6.0")
}
```

Gradle Groovy DSL (`build.gradle`):

```groovy
dependencies {
    implementation 'ai.badmonkey.agentspaces:agentspaces-micronaut:0.6.0'
}
```

```yaml
micronaut:
  application:
    name: orders
agentspaces:
  keystore: ./peer-keys
  bind: 127.0.0.1:7500
  groups:
    - name: order-fleet
      founding: order-fleet-v1
      seeds: ["hq.example:7500"]
      spaces:
        - name: tasks
        - name: findings
        - name: data
```

Every `agentspaces.*` property is the Spring Boot starter's, with the same
names and defaults (its README is the reference): the security profile,
transports and TLS, groups founded or joined, spaces with admission, content
keys, capabilities, the console, and the A2A gateway. One YAML fragment
configures a peer under either framework. Durations take ISO-8601 (`PT5M`)
or the short form (`5m`, `250ms`).
