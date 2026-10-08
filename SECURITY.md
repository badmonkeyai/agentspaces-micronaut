# Security Policy

AgentSpaces Micronaut makes a Micronaut microservice a fleet peer: its beans
become agents, its data and its HTTP presence become assets, other services
resolve it through the fleet, its management endpoints show the fleet, and
with the bridges its LangChain4j services call fleet agents as tools and its
MCP server publishes them. It inherits the security model of the AgentSpaces
core (signed, self-certifying peers and agents; the threat model in the
AgentSpaces specification, SPEC §11) and adds the surface of a service that
answers the fleet and of a model in the loop.

## Reporting a vulnerability

Please report vulnerabilities **privately** via GitHub's security advisories:

> https://github.com/badmonkeyai/agentspaces-micronaut/security/advisories/new

A vulnerability in the AgentSpaces core libraries belongs in the core
repository's advisories instead:
https://github.com/badmonkeyai/agentspaces/security/advisories/new

Do not open public issues for suspected vulnerabilities, and do not include
exploit details in public discussions until a fix is released.

We will acknowledge your report within five business days, keep you informed of
progress, credit you in the advisory unless you prefer otherwise, and
coordinate the disclosure timeline with you. There is currently no bug bounty
program.

Questions can be sent to `oss [at] badmonkey.ai`

## Scope

In scope: anything that lets an attacker violate a guarantee this project or
the core claims through the Micronaut integration. For example:
- the service asset's `get` query reaching a path outside `allowed-paths`, or
  answering a peer the authorizer does not permit;
- a card from an unauthorized issuer becoming a `@Client` target through the
  registry;
- the `/agentspaces` endpoint or the health details leaking more than the
  fleet summary and the cards the peer legitimately sees;
- a foreign AgentCard that makes a LangChain4j tool or an MCP export invoke
  something it should not, or an MCP import that executes a call the import
  space should not have admitted;
- a fleet model response accepted from an issuer not permitted
  `MODEL_SERVE`, or a conversation snapshot readable by a peer outside its
  group's content key.

Prompt injection that stays within what a tool is allowed to do is a property
of the model, not a vulnerability here. A report showing that a tool's or a
query's boundary does not hold is very welcome.

## Supported versions

During 0.x, only the latest minor release receives security fixes.
