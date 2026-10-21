---
id: 012
title: "Technology stack"
type: spike
status: done
phase: 0
depends_on: [007, 010, 011]
refs: ["PLAN Phase 0 / Spikes / 9. Technology stack", "BACKLOG Spikes / Wave 4 / 9. Technology stack", "BACKLOG MVP / Engine framework", "BACKLOG MVP / Database", "BACKLOG MVP / UI technology", "BACKLOG MVP / Sandbox technology"]
updated: 2026-10-21
---

# 012 · Technology stack

<!--
A spike answers questions and settles decisions; it does not ship production code. Run it with /sdd-spike 012:
the spike-researcher agent prepares a briefing and questions, you answer and add information over as many sessions
as it takes, and everything learned is recorded here. Prototypes are throwaway and live under specs/012-*/prototype/.
Progress counts the checkboxes in Goal questions, Questions for you, and Deliverables.
-->

## Inbox

<!-- Drop notes, links, constraints, or decisions here at any time, in any form. The next /sdd-spike session moves them into the Log and Findings and clears this section. -->

## Why

Phase 0 scaffolding cannot start until each component in the target architecture (spec 010, `docs/architecture/`) has a technology. This spike chooses them. It settles the BACKLOG.md MVP items Engine framework, Database (the product; spike 010 already made it relational), UI technology, and, with spike 10, Sandbox technology. Spike 10 (Local setup) depends on it.

Spike 8 (Driver API contract, spec 011) runs in parallel in another session. The user chose on 2026-10-21 to start this spike before it ends. Choices that depend on the contract (the HTTP layer and the MCP gateway's SDK and transport, G2) wait for spec 011 and are finished last.

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [x] **G1** Engine: which Java version, Spring Boot or a lighter framework, and which build tool?
- [x] **G2** MCP gateway and HTTP layer: which MCP SDK and version (Java SDK 2.1.0 stateful or 2.2.0 stateless, or another language), and how it fits the engine framework's server and transport support?
- [x] **G3** Database: which relational product (with document columns), which migration tool, and which library parses and validates definition YAML against the JSON Schema 2020-12?
- [x] **G4** UI: a single-page app or server-rendered pages, and which framework, given that the UI is a separately deployed static client and should be fast to build?
- [x] **G5** Runner: which language, and how it starts sandboxes as Kubernetes Jobs (client library, and a service account allowed only in the sandbox namespace; native sidecars need Kubernetes 1.33 or later)?
- [x] **G6** Broker: which product on GKE (Pub/Sub) and locally (an emulator, NATS JetStream, RabbitMQ, or other), with the Pub/Sub details recorded under spec 010 Q23 checked against current docs?
- [x] **G7** Analysis service: which language and framework?
- [x] **G8** Module rule checker for the engine's module boundaries (AD-20)?
- [x] **G9** OTel Collector distribution, the hook script language (what is reliably installed on a developer laptop), and the test approach (for example Testcontainers)?

## Briefing

<!-- Prepared by the spike-researcher agent: what the repository and current docs already say, with sources. -->

Prepared on 2026-10-21. All external facts were checked on that date.

### What the repository already settles

- **G1 Engine.** Java, one deployable, hexagonal. The core has no technology names, holds no threads, and runs one transaction per use case; a `scheduler` adapter calls `DueWork` (INTENT Runtimes; `docs/architecture/README.md`; AD-1, AD-25). The engine owns human login and sessions (AD-4). Settings come from a ConfigMap and secrets from Kubernetes Secrets (AD-22). Spec 005's prototype used Java 25, Maven, Jetty 12.1.14, and MCP SDK 2.1.0 with no Spring. `sample-crm` and the sandbox image use Java 25, the Maven Wrapper, Spring Boot 4.1.1, and Testcontainers 2.0.5 (spec 002 Q22, spec 004 plan). BACKLOG suggests Spring Boot "for speed of setup".
- **G2 MCP gateway.** Its own stateless Deployment; it forwards the caller's driver token to the engine's HTTP driver API and fails closed (AD-21). Its framework need not match the engine's. Spec 005 ran SDK 2.1.0 with the stateful `HttpServletStreamableServerTransportProvider` on plain Jetty against Claude Code 2.1.302; Claude Code tried protocol `2026-07-28` first and fell back to `2025-11-25`. The stateless Spring AI variant hit claude-code#39790. Tools take the subject as an argument, and their schemas mirror the HTTP driver API that spec 011 defines.
- **G3 Database.** Relational, ACID, row locks, unique constraints, and document columns for definition YAML and its parsed form, event payloads, and evidence payloads. Engine and analysis use separate schemas in one instance with separate credentials (AD-23, AD-24). Per-run `SELECT … FOR UPDATE`, a transactional outbox drained by `DueWork`, and idempotency records (AD-16). Definitions are validated by JSON Schema 2020-12 plus 18 semantic rules; the reference definition is about 8 KB. AD-23 names PostgreSQL "the obvious candidate"; `sample-crm` uses `postgres:18-alpine`.
- **G4 UI.** A static or thin client of the engine UI API (engine login and session cookie) and the analysis API; it holds no credential that can approve (AD-4). Pages poll. Diffs are a link to the git host's compare page (AD-27). AD-4 rejected a UI backend asserting the user with a service credential, so server-rendered pages (htmx) would have to be rendered by the engine itself. Whether the UI and both APIs share one host (path routing) is for spike 10; if not, the cookie needs CORS with credentials and `SameSite=None`.
- **G5 Runner.** One Kubernetes Job per runner job, named after the job ID (AD-16); outcomes classified from pod and container state (AD-13); service account limited to the sandbox namespace; clone step before subject code runs (AD-11); services as native sidecars (spec 002 G4). Operations: dispatch, get, cancel; the engine polls (AD-2).
- **G6 Broker.** Pub/Sub on GKE behind `EventPublisherPort`; the product name only in `broker-client`; keyed by run ID; at-least-once, deduplicated by event ID in analysis; rebuilds use the engine's paged event read (AD-24).
- **G7 Analysis.** Separate Deployment with relational projections in its own schema, progress stored in the same transaction as each projection update, introspection-based auth against the engine, no writes (AD-24).
- **G8 Module rules (AD-20).** Inbound adapters depend only on inbound ports; the core only on itself and outbound ports; each outbound adapter only on its port; no SDLC, git, or Claude Code names in the core; the broker product only in `broker-client`.
- **G9.** Collector receives OTLP/HTTP JSON on 4318 and needs contrib's `attributes` processor, secret redaction, a `file` exporter, and name mapping (AD-9). Spec 005's `hook.sh` is POSIX `sh` with `curl --fail --max-time 8` and `jq` (about 11 ms per call). The Mac has no container runtime yet (spike 10). Engine tests will want real PostgreSQL.

### Researched

- **Java.** 25 is the current LTS (Oracle Premier Support to September 2030); 27 is the current feature release; next LTS is 29, September 2027 ([Oracle roadmap](https://www.oracle.com/java/technologies/jvp-support-roadmap.html)).
- **Frameworks.** Spring Boot 4.1.1 GA, 4.2 in milestones ([spring.io](https://spring.io/projects/spring-boot)); Boot 4.1 supports Java 17–27. Quarkus 3.33 LTS, 4.0.0.Beta1 on 2026-10-01 ([quarkus.io/releases](https://quarkus.io/releases)). Micronaut 5.1.3, Java 25 baseline ([micronaut.io](https://micronaut.io/category/release-announcements/)). Javalin 7.2.3.
- **MCP Java SDK** ([releases](https://github.com/modelcontextprotocol/java-sdk/releases)): 2.1.0 latest stable; 2.2.0-M1 pre-release adds a stateless server in `io.modelcontextprotocol.modern`, passes the 37 required conformance scenarios, no tasks or SEP-2243 headers yet. Java 17 baseline; core includes a Servlet transport; WebMVC/WebFlux transports live in Spring AI 2.0+. Spring AI 2.0.1 pins MCP SDK 2.0.0, not 2.1.0; 2.1.0-M1 on 2026-09-24; stateless mode allows no server-initiated requests ([docs](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stateless-server-boot-starter-docs.html)).
- **claude-code#39790** ([issue](https://github.com/anthropics/claude-code/issues/39790)): since 2.1.84, connections fail to POST-only servers that answer GET with 405; closed "not planned" 2026-05-11. Workaround: stateful Streamable HTTP.
- **Other MCP SDKs.** Quarkus MCP Server 2.0.x serves `2026-07-28` (stateless) and `2025-11-25` (stateful) on one endpoint ([Quarkus blog](https://quarkus.io/blog/mcp-stateless/)). TypeScript SDK v2 `createMcpHandler` serves both; stability not confirmed ([MCP blog](https://blog.modelcontextprotocol.io/posts/sdk-betas-2026-07-28/)).
- **PostgreSQL** 18.6 current, EOL 2030-11-14 ([versioning](https://www.postgresql.org/support/versioning/)); 19 at RC, GA reported for 2026-11-19 (secondary source).
- **Migrations.** Flyway 13.10.3 latest; Boot 4.1 manages 12.4.0; PostgreSQL needs `flyway-database-postgresql`. Liquibase 5.0.4; Community is under the Functional Source License since 5.0 ([Liquibase blog](https://www.liquibase.com/blog/liquibase-community-for-the-future-fsl)).
- **Definitions.** networknt json-schema-validator 3.0.8 supports drafts 4–2020-12, needs Jackson 3 (2.x line uses Jackson 2). `jackson-dataformat-yaml` 3.x uses SnakeYAML Engine (YAML 1.2).
- **UI.** Vite 8.0 stable since 2026-03-12 ([vite.dev](https://vite.dev/blog/announcing-vite8)); React 19.2.x. SvelteKit `adapter-static` builds an SPA ([docs](https://svelte.dev/docs/kit/single-page-apps)). htmx 4.0.0 on 2026-08-28.
- **Kubernetes.** Supported versions 1.35–1.37 ([releases](https://kubernetes.io/releases/)), so native sidecars are always available except on an old local distribution. fabric8 kubernetes-client 8.0.0 and official `client-java` 27.0.1. Go 1.27.
- **Pub/Sub** ([ordering](https://docs.cloud.google.com/pubsub/docs/ordering), [exactly-once](https://docs.cloud.google.com/pubsub/docs/exactly-once-delivery), [properties](https://docs.cloud.google.com/pubsub/docs/subscription-properties)): ordering set per subscription at creation; 1 MBps per key; redelivering one message redelivers later ones for that key; dead-letter is best effort with ordering; exactly-once only for pull in one region (Java client 1.139.0+). Retention default 7 days, max 31; ack deadline 10 s, max 600 s; dead-letter attempts 5–100.
- **Pub/Sub emulator** ([emulator](https://docs.cloud.google.com/pubsub/docs/emulator)): supports push, pull, ordering, replay, dead-letter, retry, filtering. Not IAM, retention settings, `UpdateTopic`, or seek-to-timestamp on ordered subscriptions. State lasts only for the emulator's life; port 8085; client libraries only. NATS server stable line 2.14.x.
- **Module rules.** ArchUnit 1.5.1; Spring Modulith 2.1.1, whose `verify()` can add jMolecules' `ensureHexagonal` ([docs](https://docs.spring.io/spring-modulith/reference/verification.html)).
- **Collector.** v0.163.1 (2026-10-16), same as spec 006; ocb at the same version; core and contrib distributions published.
- **Hooks.** The native Claude Code binary needs no Node ([setup](https://code.claude.com/docs/en/setup)). Shell-form command hooks run with `sh -c`; exec form skips the shell ([hooks](https://code.claude.com/docs/en/hooks)).
- **Owner's Mac** (checked by this session): macOS 26.5.1 ships `/usr/bin/jq` (`jq-1.7.1-apple`); `/usr/bin/python3` exists next to Homebrew's Python 3.14.7, with Command Line Tools installed.
- **Testcontainers** Java 2.0.5; actively supports only Docker Desktop and Docker on Linux.

## Questions for you

<!-- Prepared by the agent, highest impact first; each says which goal questions it serves, why it matters, the options, and a suggested answer. Ticked when answered or skipped. -->

- [x] **Q1** (G1, G2, G8) Spring Boot 4.1 or a lighter framework (Quarkus 3.33 LTS, Micronaut 5.1, Javalin 7.2, plain Jetty) for the engine? *Suggested:* Spring Boot 4.1.x: same stack as `sample-crm`; Security, Testcontainers service connections, and Modulith come with it.
- [x] **Q2** (G2, G5, G7) Java for every server-side service, or the best-fit language per component (Go runner, TypeScript MCP gateway, Python analysis)? *Suggested:* Java everywhere, plus TypeScript for the UI.
- [x] **Q3** (G1, G8) Plain Java core with wiring and transactions in adapters, or framework annotations allowed in the core? *Suggested:* plain Java core; transactions opened at the inbound port adapter.
- [x] **Q4** (G8) Module rules by separate build modules plus ArchUnit, ArchUnit only, or Spring Modulith `verify()` with `ensureHexagonal`? *Suggested:* build modules for core, ports, and each adapter, plus ArchUnit for the name bans and the broker rule.
- [x] **Q5** (G1) Maven or Gradle? *Suggested:* Maven with the Wrapper, as spec 005 and `sample-crm`.
- [x] **Q6** (G1, G3) Data access: plain SQL (`JdbcClient`), jOOQ, or JPA? *Suggested:* plain SQL via `JdbcClient`: explicit locks, `jsonb`, outbox with `SKIP LOCKED`.
- [x] **Q7** (G3) PostgreSQL 18 for the MVP? *Suggested:* yes, 18.6, the image `sample-crm` uses.
- [x] **Q8** (G2) GA-only MCP SDK (2.1.0 stateful, one gateway replica), or a milestone (2.2.0-M1 stateless) if a trial with Claude Code passes, or another SDK serving both protocols? *Suggested:* GA only, 2.1.0 stateful, one replica.
- [x] **Q9** (G4) Which UI framework do you know best or prefer to review: React 19.2 + Vite 8 + TypeScript, a SvelteKit SPA, or another? *Suggested:* React + Vite + TypeScript.
- [x] **Q10** (G6) Local broker: Pub/Sub emulator, NATS JetStream, or RabbitMQ? *Suggested:* the Pub/Sub emulator, so one `broker-client` adapter serves both.
- [x] **Q11** (G9) May the driver kit require `jq` next to `sh` and `curl`? *Suggested:* yes; macOS 26 ships `/usr/bin/jq`, and the kit setup checks for it on Linux.
- [x] **Q12** (G9) Testcontainers for the engine's integration tests (real PostgreSQL, Pub/Sub emulator)? It needs a Docker-compatible runtime on the Mac (spike 10). *Suggested:* yes.

- [x] **Q13** (G2) Once spec 011's contract lands: is the HTTP layer as recommended (Spring MVC controllers in the engine's inbound HTTP adapter, mirrored by the gateway's tools), and is the API written OpenAPI-first or code-first?

Suggested without a question, to confirm in the recommendation: Java 25 LTS; Flyway with SQL migrations, one set per schema; networknt json-schema-validator 3.0.8 with Jackson 3 YAML; OTel Collector contrib image v0.163.1 pinned by digest, not an ocb build.

## Log

<!-- Append-only, oldest first: "- YYYY-MM-DD · <source> · <what was answered, added, or found>". -->

- 2026-10-21 · note from the user · Start this spike while spike 8 (spec 011) runs in another session; MCP and HTTP layer choices wait for its contract.
- 2026-10-21 · research · spike-researcher briefing written into Briefing; 12 questions added.
- 2026-10-21 · research · The owner's Mac (macOS 26.5.1) has `/usr/bin/jq` 1.7.1-apple, `/usr/bin/python3`, Homebrew Python 3.14.7, and Command Line Tools.
- 2026-10-21 · answer to Q1 · Spring Boot 4.1 for the engine.
- 2026-10-21 · answer to Q2 · Java for every server-side service (engine, MCP gateway, runner, analysis); TypeScript only for the UI.
- 2026-10-21 · answer to Q3 · Framework annotations allowed in the core. Conflicts with `docs/architecture/README.md` (core "has no technology or provider names", AD-20); asked the user which holds.
- 2026-10-21 · answer to Q4 · Separate build modules for the core, the ports, and each adapter, plus ArchUnit for the name bans and the broker-only-in-`broker-client` rule.
- 2026-10-21 · answer to Q3 conflict · Allow Spring annotations in the core. AD-20 and `docs/architecture/README.md` are to be amended: the core may import Spring annotations (such as `@Service`, `@Transactional`); neutrality applies to domain and provider names (SDLC, git, Claude Code, broker product), not to the framework.
- 2026-10-21 · answer to Q5 · The user asked for the advantages of Maven and Gradle before choosing.
- 2026-10-21 · answer to Q6 · JPA / Hibernate for data access.
- 2026-10-21 · answer to Q7 · PostgreSQL 18 (18.6).
- 2026-10-21 · answer to Q8 · GA only: MCP Java SDK 2.1.0, stateful Streamable HTTP, one gateway replica in the MVP.
- 2026-10-21 · answer to Q9 · React + Vite + TypeScript for the UI.
- 2026-10-21 · answer to Q10 · Pub/Sub emulator as the local broker.
- 2026-10-21 · answer to Q5 · Maven with the Wrapper, after comparing it with Gradle (Maven: same toolchain as `sample-crm` and spec 005, declarative POMs easy to review and edit; Gradle: faster builds and convention plugins, but a second toolchain and build scripts as code).
- 2026-10-21 · answer to Q11 · The user asked what jq is.
- 2026-10-21 · answer to Q12 · The user asked whether Testcontainers integrates with a local Kubernetes cluster.
- 2026-10-21 · answer to Q11 · After an explanation of jq (a command-line JSON processor the hook script uses to read Claude Code's hook JSON and build the engine request): the kit may require `sh`, `curl`, and `jq`.
- 2026-10-21 · answer to Q12 · After an explanation that Testcontainers talks to a Docker API and runs test containers beside the local cluster on the same Docker-compatible runtime, not inside Kubernetes: the engine's integration tests use Testcontainers.
- 2026-10-21 · note from the user · Agreed the recommendation table as drafted. Run the hello-world and the definition parsing trial now; the JPA and Pub/Sub emulator trials move to spike 10 or the scaffolding.
- 2026-10-21 · research (hello-world) · Built `prototype/hello-world/` (Maven multi-module, Spring Boot 4.1.1 parent, Java 25.0.3). The gateway hosts MCP SDK 2.1.0's stateful servlet transport through a `ServletRegistrationBean` (async supported) in Boot's Tomcat 11.0.24, with no Spring AI. Claude Code 2.1.303 (`claude -p --mcp-config`, Haiku) connected and called `status`; the engine's JSON came back through the gateway with the token forwarded. Wrong token: the gateway's filter got 401 from the engine's `/v1/whoami` and returned 401; Claude Code marked the server failed (`AUTH_HEADER_REJECTED`), no tools. Engine down at connect: 503, server failed, no tools. Engine down after initialize: a `tools/call` got 503. All fail closed. A first attempt on port 8787 returned 404s because another local Python process listened on 127.0.0.1:8787; moved to 18787 and 18788.
- 2026-10-21 · research (parsing trial) · `definition-trial/`: Jackson YAML 3.1.5 (managed by Boot 4.1.1) works with networknt 3.0.8 (built against Jackson 3.2.1). The reference definition parses and passes a partial draft 2020-12 schema. Unquoted `no`, `yes`, `on`, `off` labels are read as strings (YAML 1.2) and pass; unquoted `true`, `false` are booleans and fail ("boolean found, string expected"). A duplicate key fails at parse with line and column (`STRICT_DUPLICATE_DETECTION`). An unknown top-level key and a missing required `to` fail with JSON-pointer locations. The tree keeps no locations, so the trial maps pointers to lines from the token stream; lines 36 and 96 matched the file.
- 2026-10-21 · note from the user · Accept YAML 1.2: an unquoted `no` is the string "no". Update `docs/definitions/schema.md` and change its fixture to an unquoted `true` label.
- 2026-10-21 · note from the user · Keep G2 open until spec 011's contract lands, then confirm the HTTP layer against it.
- 2026-10-21 · note from the user · Spec 011 (Driver API contract) is closed and merged to main (PR #10, commit 19cda6a).
- 2026-10-21 · research · Read `docs/driver-api/README.md` and spec 011 from `origin/main`. What it needs from the stack: every path under `/v1`; bearer driver token on every call; an `Idempotency-Key` header on every mutation (IETF draft semantics: stored result on retry, refusal on a different payload); denials as RFC 9457 `application/problem+json`, except the gate, which always answers 200; MCP tools mirroring part of the HTTP API, with `outputSchema` (JSON Schema 2020-12) and conforming `structuredContent`, business errors as `isError: true`; schemas turned into JSON Schema files with tests in milestone 1. Checked: MCP SDK 2.1.0's `Tool.Builder` has `outputSchema` and `CallToolResult.Builder` has `structuredContent`; Spring Framework 7 (Boot 4.1) has built-in `ProblemDetail` for RFC 9457. No library provides `Idempotency-Key` handling; it is an engine filter over an idempotency table (spec 010 AD-16), designed in spike 9b.
- 2026-10-21 · answer to Q13 · Schema-first with no code generation: hand-written JSON Schema 2020-12 files are the contract, wrapped in an OpenAPI 3.1 document; Java records written by hand; contract tests validate requests and responses with networknt; the gateway uses the same files as its MCP input and output schemas.
- 2026-10-21 · note from the user · Docker is installed; run the container trials. Docker Desktop 29.5.2 (linux/arm64) was installed but not running; started it.
- 2026-10-21 · research (JPA trial) · `prototype/hello-world/jpa-trial/`, 5 tests passing on Testcontainers 2.0.5 with `postgres:18-alpine` (PostgreSQL 18), Hibernate 7.4.5 and Flyway 12.4.0 as managed by Boot 4.1.1. Flyway created the schema and `ddl-auto=validate` accepted a `jsonb` column mapped with `@JdbcTypeCode(SqlTypes.JSON)` to a `Map`. A `jsonb` round trip kept nested values, and a native `state ->> 'stage'` query found the row. A partial unique index (`where status = 'active'`) allowed an inactive duplicate and refused a second active run with `DataIntegrityViolationException` naming `one_active_run`. `@Lock(PESSIMISTIC_WRITE)` emitted `for no key update` (not `for update`); a second transaction with lock timeout 0 (`nowait`) failed with `PessimisticLockingFailureException` while the first held the lock, and succeeded after. Lock timeout -2 emitted `for no key update ... skip locked`, and two concurrent claims of 2 outbox rows each got disjoint rows. Nested repository interfaces need `@EnableJpaRepositories(considerNestedRepositories = true)`, and nested entities an explicit `@Entity(name = …)` for JPQL.
- 2026-10-21 · research (Pub/Sub emulator trial) · `prototype/hello-world/pubsub-trial/`, 3 tests passing with `google-cloud-pubsub` (libraries-bom 26.90.0) on the emulator image `google-cloud-cli:emulators` through Testcontainers' `PubSubEmulatorContainer`. With the run ID as ordering key and an ordered subscription, each run's events arrived in order while runs interleaved. Nacking a run's messages redelivered them in order with `deliveryAttempt` 2. With `maxDeliveryAttempts: 5`, the message was delivered 5 times and then appeared on the dead-letter topic, on both an ordered and an unordered subscription. A pull with no messages blocks for about 60 s unless the call has a short timeout. After `docker restart`, the emulator had no topics: all state is lost.

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1

- **Decision** (agreed recommendation): Java 25 LTS.
- **Decision** (user, Q1): Spring Boot 4.1 (4.1.1 is the current GA).
- **Fact** (hello-world): a Spring Boot 4.1.1 web app on Java 25.0.3 starts in about 1 s; the multi-module Maven build works with the Boot parent.
- **Decision** (user, Q2): the engine is Java.
- **Decision** (user, Q3): core modules may use Spring annotations such as `@Service` and `@Transactional`. This amends AD-20's "no technology names" in the core: neutrality covers domain and provider names (SDLC, git, Claude Code, the broker product), not the framework.
- **Decision** (user, Q5): Maven with the Maven Wrapper, one multi-module build (parent POM managed by Spring Boot); CI runs `./mvnw -B verify`.
- **Decision** (user, Q6): data access through JPA / Hibernate (Spring Data JPA).
- **Fact** (JPA trial, PostgreSQL 18, Hibernate 7.4.5): JPA covers the storage patterns. `@Lock(PESSIMISTIC_WRITE)` takes the per-run row lock as `FOR NO KEY UPDATE`, which serializes use cases on the run row but, unlike `FOR UPDATE`, does not block inserts of rows that reference it by foreign key (such as events and outbox rows); lock timeout 0 gives `NOWAIT`. Lock timeout -2 gives `SKIP LOCKED`, and concurrent outbox claims get disjoint rows. `jsonb` maps with `@JdbcTypeCode(SqlTypes.JSON)` and passes `ddl-auto=validate`. The "one active run" rule is a partial unique index whose violation names the index in `DataIntegrityViolationException`.
- **Assumption** (for spike 9b): session state (dirty checking, flush order) must not hide writes from the replay check; spike 9b designs the flush and event-write order.

### G2

- **Decision** (user, Q2): the MCP gateway is written in Java.
- **Decision** (user, Q8): GA releases only. MCP Java SDK 2.1.0 with the stateful Streamable HTTP servlet transport (proven in spec 005), one gateway replica in the MVP, since stateful MCP sessions live in memory. A later move to a stateless SDK touches only the gateway (AD-21).
- **Fact** (hello-world): Spring Boot 4.1.1 hosts the SDK 2.1.0 `HttpServletStreamableServerTransportProvider` directly as a servlet (`ServletRegistrationBean`, async supported) in Tomcat 11; Spring AI is not needed. Claude Code 2.1.303 connected and called `status`. A servlet filter that checks the caller's token with the engine on each MCP request gives 401 for a bad token and 503 when the engine is down, so the gateway fails closed and keeps no state of its own.
- **Decision** (agreed recommendation): the gateway is a Spring Boot app hosting the SDK servlet transport directly.
- **Fact** (spec 011, `docs/driver-api/README.md`): the contract needs `/v1` paths, a bearer token, `Idempotency-Key` on mutations, RFC 9457 problem responses (the gate always 200), and MCP tools with `outputSchema` and `structuredContent`. SDK 2.1.0 supports `outputSchema` and `structuredContent`; Spring Framework 7 has `ProblemDetail` built in. `Idempotency-Key` is an engine filter over the idempotency table (AD-16), designed in spike 9b.
- **Decision** (agreed recommendation): the engine's HTTP API is Spring MVC controllers in the inbound HTTP adapters; denials use Spring's `ProblemDetail`.
- **Decision** (user, Q13): schema-first with no code generation. Hand-written JSON Schema 2020-12 files are the contract, wrapped in an OpenAPI 3.1 document; Java records are written by hand; contract tests validate requests and responses with networknt; the MCP gateway uses the same files as its tools' input and output schemas.

- **Decision** (user, 2026-10-21): this question waits for spec 011's driver API contract and is closed last.

### G3

- **Decision** (user, Q7): PostgreSQL 18 (18.6, supported until 2030-11-14), the same major version `sample-crm` uses.
- **Decision** (user, Q6): JPA / Hibernate; see G1.
- **Decision** (agreed recommendation): Flyway with SQL scripts, one set per schema; Hibernate `ddl-auto=validate`.
- **Decision** (agreed recommendation): Jackson 3 YAML with `STRICT_DUPLICATE_DETECTION`, and networknt json-schema-validator 3.x (draft 2020-12).
- **Fact** (parsing trial): Jackson YAML 3.1.5 as managed by Boot 4.1.1 works with networknt 3.0.8, which is built against Jackson 3.2.1. The reference definition parses and passes a partial schema. Duplicate keys fail at parse with line and column. Schema errors carry JSON-pointer locations only; mapping them to YAML lines needs a pass over the token stream, which the trial did in about 30 lines.
- **Decision** (user, 2026-10-21): YAML 1.2 holds. Unquoted `no`, `yes`, `on`, `off` are strings; unquoted `true`, `false` are booleans and fail where a string is required. `docs/definitions/schema.md` and its "unquoted `no`" fixture change accordingly.

### G4

- **Decision** (user, Q2): the UI is written in TypeScript.
- **Decision** (user, Q9): a static single-page app with React (19.2.x) and Vite (8.x), built to static files.

### G5

- **Decision** (user, Q2): the runner is written in Java.
- **Decision** (agreed recommendation): a Spring Boot app using fabric8 kubernetes-client 8.x, with a namespaced Role bound to its service account in the sandbox namespace only. Every supported Kubernetes version (1.35–1.37) has native sidecars.

### G6

- **Decision** (user, Q10): the Pub/Sub emulator on the local cluster, Pub/Sub on GKE, so one `broker-client` adapter serves both, using the `google-cloud-pubsub` Java client. The emulator loses its state on restart; analysis rebuilds from the engine's paged event read (AD-24).
- **Fact** (emulator trial): with the run ID as ordering key, each run's events arrive in order; a nack redelivers a key's messages in order; dead-lettering after `maxDeliveryAttempts` works on ordered and unordered subscriptions. After a restart the emulator has no topics or subscriptions, so the local setup must create them (idempotently) whenever the emulator starts. A pull with nothing to deliver blocks about 60 s unless the call sets a short timeout.

### G7

- **Decision** (user, Q2): the analysis service is written in Java.
- **Decision** (agreed recommendation): Spring Boot with JPA, in its own schema.

### G8

- **Decision** (user, Q3): ArchUnit does not ban Spring imports in the core.
- **Decision** (user, Q4): separate build modules for the core, the ports, and each adapter, so a wrong dependency does not compile; ArchUnit tests check the banned names (SDLC, git, Claude Code) in the core and that the broker product appears only in `broker-client`.

### G9

- **Decision** (agreed recommendation): the `otelcol-contrib` distribution, v0.163.1, pinned by digest; no custom ocb build.
- **Decision** (agreed recommendation): Vitest for the UI's tests.
- **Decision** (user, Q11): hook scripts are POSIX `sh` using `curl` and `jq`, as spec 005's `hook.sh`. The kit setup checks for `jq` and names it as a prerequisite on Linux.
- **Decision** (user, Q12): the Java services' integration tests use Testcontainers (real PostgreSQL 18; the Pub/Sub emulator where needed), with Spring Boot service connections.
- **Fact** (Testcontainers docs, via spec 002): Testcontainers needs a Docker API and actively supports only Docker Desktop and Docker on Linux; OrbStack and Colima are best effort. Its containers run beside the local cluster, not in it. Spike 10 must pick a runtime that serves both the cluster and Testcontainers.
- **Fact** (container trials, 2026-10-21): Testcontainers 2.0.5 works on the owner's Mac with Docker Desktop 29.5.2 (arm64), for PostgreSQL 18 and the Pub/Sub emulator.
- **Fact** (checked 2026-10-21): macOS 26.5.1 on the owner's Mac ships `/usr/bin/jq` (`jq-1.7.1-apple`), so `sh` + `curl` + `jq` needs no install there.

## Recommendation and decisions

<!-- Drafted when every goal question is answered, and agreed with the user. -->

| Decision | Choice | Alternatives considered | Why |
| --- | --- | --- | --- |
| Java version | Java 25 LTS | Java 27 (non-LTS) | Same as `sample-crm` and the sandbox image; supported to 2030 |
| Engine framework | Spring Boot 4.1.x (Q1) | Quarkus 3.33, Micronaut 5.1, Javalin 7 / plain Jetty | One stack with `sample-crm`; Security, Testcontainers service connections, scheduling built in |
| Framework in the core | Spring annotations allowed in core modules (Q3); amends AD-20 | Plain Java core | Less wiring; neutrality means no domain or provider names, not no framework |
| Build tool | Maven with the Wrapper, one multi-module build (Q5) | Gradle | One toolchain with `sample-crm` and spec 005; declarative POMs |
| Module rule checker | Build modules per core, port, and adapter, plus ArchUnit 1.5.x (Q4) | ArchUnit only; Spring Modulith `verify()` | A wrong dependency does not compile; ArchUnit covers the name bans and the broker rule |
| Database | PostgreSQL 18 (Q7) | PostgreSQL 19 (not GA); another product | `jsonb`, row locks, `SKIP LOCKED`; same major version as `sample-crm` |
| Data access | Spring Data JPA / Hibernate (Q6) | `JdbcClient`; jOOQ | User's choice; locks and `jsonb` via Hibernate features, to confirm in the JPA trial |
| Migrations | Flyway, SQL scripts, one set per schema; Hibernate `ddl-auto=validate` | Liquibase | Managed by Boot; Liquibase Community is under the FSL since 5.0 |
| Definition parsing and validation | Jackson 3 YAML (SnakeYAML Engine, YAML 1.2) and networknt json-schema-validator 3.x | SnakeYAML 1.x (YAML 1.1); other validators | Jackson 3 is Boot 4's; networknt supports draft 2020-12 |
| MCP gateway | Java, Spring Boot hosting MCP Java SDK 2.1.0's stateful servlet transport directly; one replica (Q2, Q8); tool list from spec 011 | Spring AI MCP starter (pins SDK 2.0.0, stateless hit claude-code#39790); SDK 2.2.0-M1 stateless; TypeScript or Quarkus | Proven with Claude Code in spec 005; GA only |
| Runner | Java, Spring Boot, fabric8 kubernetes-client 8.x; a namespaced Role in the sandbox namespace (Q2) | Official `client-java`; Go with client-go | Java everywhere; fabric8 has fluent builders and a mock server for tests |
| Broker | Pub/Sub on GKE, Pub/Sub emulator locally; `google-cloud-pubsub` client only in `broker-client` (Q10) | NATS JetStream; RabbitMQ | One adapter for both environments |
| Analysis service | Java, Spring Boot, JPA, its own schema (Q2) | Python | Java everywhere |
| UI | React 19.2 + Vite 8 + TypeScript, a static single-page app (Q9) | SvelteKit SPA; htmx; Angular | A static client fits AD-4; largest ecosystem |
| OTel Collector | `otelcol-contrib` v0.163.1, pinned by digest | A custom ocb build | Already has the `attributes` processor, redaction, and `file` exporter |
| Engine HTTP layer | Spring MVC controllers; RFC 9457 denials via Spring `ProblemDetail`; `Idempotency-Key` as an engine filter over the idempotency table (spike 9b) | Spring WebFlux | Blocking request-per-transaction fits AD-25 and JPA |
| API contract format | Schema-first, no codegen: hand-written JSON Schema 2020-12 files in an OpenAPI 3.1 document; hand-written records; networknt contract tests; the same files as MCP tool schemas (Q13) | openapi-generator codegen; code-first with springdoc | One source for HTTP and MCP; same validator as definitions; spec before code (Art. 13) |
| Hook script | POSIX `sh` with `curl` and `jq` (Q11) | Raw JSON forwarding; a compiled helper | Proven in spec 005; `jq` ships with macOS |
| Tests | JUnit 5 and Testcontainers 2.x for the Java services (Q12); Vitest for the UI | A database in the cluster; embedded PostgreSQL | Real PostgreSQL behaviour for locks and `jsonb` |

## Deliverables

- [x] Every goal question answered, and the recommendation agreed with the user
- [x] A stack table with the choice, the reason, and the rejected alternative for each component
- [x] A hello-world through the chosen engine framework and MCP SDK, under `prototype/`
- [x] BACKLOG.md items settled by this spike updated

## Follow-ups

<!-- New specs, BACKLOG items, experiments, or open questions this spike created. -->

- Done at verify: `docs/definitions/schema.md` reads YAML 1.2, and its "unquoted `no`" fixture is now an unquoted `true` label (Q3 conflict, parsing trial).
- Done at verify: AD-20 in `docs/architecture/decisions.md` and the core description in `docs/architecture/README.md` allow Spring annotations in the core (Q3, Q4).
- Done during the spike: the hello-world, the definition parsing trial, the JPA trial, and the Pub/Sub emulator trial (`prototype/hello-world/`).
- BACKLOG spike 9b input: confirm `FOR NO KEY UPDATE` is enough for every use case; design the flush and event-write order under JPA for the replay check; run the module-rule trial (one planted violation per rule); `Idempotency-Key` handling as an engine filter.
- BACKLOG spike 10 input: create the Pub/Sub emulator's topics and subscriptions idempotently at every start; one MCP gateway replica; `otelcol-contrib` v0.163.1 by digest; Docker Desktop 29.5.2 runs Testcontainers on the owner's Mac.
- BACKLOG "Next": **Stateless MCP gateway**, the 2.2.x stateless SDK trial, once GA (Q8).
- Milestone 1 (already in PLAN.md Phase 1): the formal definition JSON Schema and the 11 invalid fixtures, now with the chosen libraries.

## Changelog

- 2026-10-21 Created.
- 2026-10-21 Briefing and questions prepared; in progress.
- 2026-10-21 Q1–Q12 answered, recommendation agreed, hello-world and parsing trial run; G2 open until spec 011 lands.
- 2026-10-21 Spec 011 merged; Q13 answered and G2 closed. Every goal question answered.
- 2026-10-21 JPA and Pub/Sub emulator trials run on Docker Desktop.
- 2026-10-21 Verified and closed: BACKLOG.md (Engine framework, Database, UI technology, Sandbox technology in part, spikes 9, 9b, 10, Next), PLAN.md, CLAUDE.md, AD-20, and schema.md updated.
