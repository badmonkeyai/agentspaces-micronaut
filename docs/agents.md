# Agents

Written for: Micronaut developers who build with agentspaces-micronaut.

`@SpaceAgent` is a bean-defining stereotype (a singleton) that is also an
`@AgentSpec`. At startup the library finds every bean carrying `@AgentSpec`
(directly or through a stereotype) or `@ProvidesCapability`, instantiates
it, and binds it: worker loops start, bids wire, and the bean's AgentCard
publishes into every joined group whose spaces satisfy its annotations.

```java
@SpaceAgent(name = "orders-service", description = "Prices customer orders")
public class Pricer {

    @SpaceTake(space = "tasks", resultSpace = "findings", lease = "1m")
    public PricedOrder price(PriceRequest request) { ... }
}
```

A `@Controller` can call the same method for HTTP: one implementation, two
front doors, and the AgentCard lists `price` as an action any fleet agent
invokes through `RemoteActions`. Keep the agent bean free of AOP interceptor
bindings (`@Retryable`, `@Cacheable`, `@Transactional`, a custom `@Around`):
Micronaut generates a subclass that hides the annotations from the binder,
and such a bean fails at startup saying so. HTTP concerns such as
`@ExecuteOn` belong on the controller that delegates to the agent.

Every binding annotation of the core binds here: the 0.2.0 set
(`@SpaceTake`, `@SpaceNotify`, `@BidFunction`, `@Ballot`, `@OrderedTake`,
`@OnDecision`, `@SpaceRef`, `@CapabilityRef`, `@ProvidesCapability`) and the
0.3.0 set (`@SpaceJoin` with its `@Part`s, `@Propose`, `@OnEstimate`,
`@SpaceReduce`). A bean naming a space no group registers fails at startup
listing the spaces (including a join's part spaces, a proposal's vote space,
an estimate's result space, and a reduce's space) and the configured groups.
A plain bean whose only fleet annotation is `@SpaceReduce` enrolls like any
other (the core binder's bindings cover the reduce since 2026-10-09).
`core/src/test/.../AnnotationPassthroughTest` proves each of these through
Micronaut-managed beans, reading every outcome back from a space: the
`@SpaceNotify` fork (`Entries` of a `Tagged` entry, a `@SpaceRef` write), the
`@BidFunction` on an AUCTION space, `@Propose` with `@Ballot`, `@OnDecision`
and a `@CapabilityRef` client, a `Motion` return, a `Contribution` settled by
`@OnEstimate`, a `LEASED` `@SpaceJoin` over a `keyTag` part, `@SpaceReduce`
with `keyTag` and `accumulatorLease`, `@OrderedTake` over a two-peer ordered
log (a `@Singleton` registers the `OrderedTakes` coordinator before the clerk
enrolls; a one-member Raft log never elects a leader), and a
`@ProvidesCapability` bean advertised into the group.
`CompileTimeEnrollTest` covers the processor path for the 0.3.0 set.

A bean whose only fleet annotations are on methods (a plain `@Singleton`
with a `@SpaceNotify` or `@SpaceJoin` method) enrolls when the application
compiles with the processor module, which maps all ten method annotations
(`@SpaceTake`, `@SpaceNotify`, `@BidFunction`, `@Ballot`, `@OrderedTake`,
`@OnDecision`, `@SpaceJoin`, `@Propose`, `@OnEstimate`, `@SpaceReduce`) to
startup executables and also keeps the binder's reflection in native images:

```xml
<!-- maven-compiler-plugin annotationProcessorPaths, after micronaut-inject-java -->
<path>
  <groupId>ai.badmonkey.agentspaces</groupId>
  <artifactId>agentspaces-micronaut-processor</artifactId>
  <version>0.6.0</version>
</path>
```

```kotlin
// build.gradle.kts, under the Micronaut Gradle plugin
dependencies {
    annotationProcessor("ai.badmonkey.agentspaces:agentspaces-micronaut-processor:0.6.0")
}
```

```groovy
// build.gradle
dependencies {
    annotationProcessor 'ai.badmonkey.agentspaces:agentspaces-micronaut-processor:0.6.0'
}
```
