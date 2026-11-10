# How it is built

Written for: Micronaut developers who build with agentspaces-micronaut.

The library reuses the Spring starter's wiring without Spring. The published
`agentspaces-spring-boot-autoconfigure` jar carries no Spring classes (it
compiles against provided-scope stubs), and its properties POJO, authorizers,
and build methods have no Spring type in their signatures; the JVM ignores
the Spring annotations it cannot resolve. A Micronaut `@Factory` binds the
`agentspaces` subtree of the environment into that POJO and calls those
methods, so the transport, TLS, group, space, key-ring, and capability wiring
lives in one place for both frameworks. The suite runs with no Spring on the
classpath and pins that assumption.

Jackson: Micronaut 5 is on Jackson 3; the core is on Jackson 2.21.7, with
`jackson-annotations` 2.22 for Micronaut. Transport: TCP and TLS;
`agentspaces-transport-quic` pins Netty 4.1 and Micronaut manages 4.2, so
QUIC is not supported here.
