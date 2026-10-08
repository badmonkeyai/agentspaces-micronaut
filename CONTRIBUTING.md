# Contributing to AgentSpaces Micronaut

Thank you for your interest in contributing. AgentSpaces Micronaut is an
Apache 2.0 project from Bad Monkey, Inc., and everything in this repository
stays Apache 2.0.

This repository holds the Micronaut integration: the fabric from the
`agentspaces.*` properties, annotated beans enrolled as AgentCards, assets
and the service card, the fleet as a service registry, management views, the
console and A2A gateway, the LangChain4j bridge, and the MCP bridge. Changes
to the coordination fabric itself (peering, spaces, capabilities, the
annotation model) belong in the
[AgentSpaces core repository](https://github.com/badmonkeyai/agentspaces);
changes to the model-service wire records belong in
[agentspaces-model-wire](https://github.com/badmonkeyai/agentspaces-model-wire).

## Before you start

- **Questions and ideas** are welcome as [GitHub issues](https://github.com/badmonkeyai/agentspaces-micronaut/issues).
- **Security issues** must not be filed as public issues. See
  [SECURITY.md](SECURITY.md) for private reporting.
- **Code of conduct:** by participating you agree to the
  [code of conduct](CODE_OF_CONDUCT.md).

## How the project is built

- **Micronaut 5.2 and Java 21.** The library is Java 21 bytecode; Micronaut
  5.2 itself is Java 25 bytecode, so build and test on JDK 25.
- **Compile-time everything.** Every bean is compiled with
  `micronaut-inject-java`. No classpath scanning, no `@Import` in
  applications, and every feature behind a `@Requires` on its property and,
  where it needs an optional module, on its classes.
- **The fabric wiring is the Spring starter's.** The base library calls the
  public build methods of `agentspaces-spring-boot-autoconfigure`, whose
  published jar carries no Spring classes, and the suite runs with no
  Spring on the classpath to pin that. Do not add Spring to the classpath,
  and do not re-implement what those methods do.
- **Replaceable defaults.** A bean the library defines yields to an
  application bean of the same type (`@Requires(missingBeans = ...)` or
  `@Secondary`). Keep it that way for anything new.
- **One connector runtime per peer**, a scheduled refresh that never lets a
  listener's exception kill it, and no AOP interceptor binding on an agent
  bean: the README explains each, and the tests pin them.

## Tests

`mvn clean verify` runs every suite; `-Pexamples` adds the two example
applications. Fleet tests run over TCP on free ports in one JVM, with
scripted models and no live provider. A new feature comes with a context
test that proves it switches on and off with its property, backs off when
its module is absent, and yields to an application bean, plus a flow test
across two peers where the feature crosses the fleet.

## Pull requests

- One change per pull request, with a test that would have failed before it.
- Keep the README property tables and the javadoc current: the javadoc jar
  is part of the release.
- Run `mvn clean verify -Pexamples` on JDK 25 before opening the pull
  request; CI runs the same, with the publication profile unsigned, plus a
  weekly dependency scan.
