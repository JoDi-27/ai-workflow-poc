---
id: 013
title: "Engine low-level design"
type: spike
status: done
phase: 0
depends_on: [003, 010, 011, 012]
refs: ["PLAN Phase 0 / Spikes / 9b. Engine low-level design", "BACKLOG Spikes / Wave 4 / 9b. Engine low-level design"]
updated: 2026-10-27
---

# 013 · Engine low-level design

<!--
A spike answers questions and settles decisions; it does not ship production code. Run it with /sdd-spike 013:
the spike-researcher agent prepares a briefing and questions, you answer and add information over as many sessions
as it takes, and everything learned is recorded here. Prototypes are throwaway and live under specs/013-*/prototype/.
Progress counts the checkboxes in Goal questions, Questions for you, and Deliverables.
-->

## Inbox

<!-- Drop notes, links, constraints, or decisions here at any time, in any form. The next /sdd-spike session moves them into the Log and Findings and clears this section. -->

## Why

The Phase 1 engine specs need a design they can implement directly. Spike 7 (spec 010, `docs/architecture/`) fixed the engine's modules, ports, and decisions AD-1 to AD-29; spike 8 (spec 011, `docs/driver-api/`) fixed the driver contract; spike 9 (spec 012) fixed the stack: Java 25, Spring Boot 4.1, Maven modules per core, port, and adapter with ArchUnit, JPA / Hibernate on PostgreSQL 18, and Flyway. This spike turns those into packages, a domain model, a schema, state machines, a transaction pattern, a validator design, a denial model, and a test approach. It settles no BACKLOG.md MVP item on its own; it feeds the Phase 1 items Definition model and Driver API. It can run alongside spike 10 (Local setup).

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [x] **G1** Package and module structure inside the one engine deployable (AD-1): inbound adapters (`driver-api`, `ui-api`, `telemetry-ingestion`, `event-read`, `scheduler`), inbound ports (`DriverOperations`, `HumanOperations`, `TelemetryIntake`, `EventFeed`, `DueWork`), core modules (identity, definitions, runs, checkpoints, evaluation-tasks, scoring, typed-decisions, outbox + deadlines), outbound ports (`RunnerPort`, `DecisionEngine`, `EventPublisherPort`), and outbound adapters (`runner-client`, `systemone`, `broker-client`). How the dependency rules (AD-20, AD-25) are written and checked in the build, including the module-rule trial with one planted violation per rule (deferred from spec 012)?
- [x] **G2** The domain model as classes or records and aggregates: the run aggregate and its boundary, checkpoint attempts, step results, typed decision records and tree walks, leases, driver sessions, and evidence references (AD-27). Which state lives in which aggregate, and which invariants each guards?
- [x] **G3** The relational schema: tables, keys, and unique constraints (for example one active run per workflow and subject key), document columns (AD-23), the event table, idempotency records, the outbox, deadlines, and the provider call table (spike 6b). The migration approach, and how operational values that affect an outcome are recorded for replay?
- [x] **G4** The state machines in code, with their transition guards: run, checkpoint attempt, step result, evaluation task, runner job, definition version, and decision-tree walk?
- [x] **G5** The transaction and concurrency pattern per use case: the run-row lock (is Hibernate's `PESSIMISTIC_WRITE`, which is `FOR NO KEY UPDATE` on PostgreSQL, enough for every use case?), idempotency handling (`Idempotency-Key` as an engine filter over the idempotency table), writing state, events, and outbox rows in one transaction (AD-5, AD-16) with a flush and event-write order that JPA session state cannot hide from the replay check, and how `DueWork` claims due rows safely with more than one engine replica?
- [x] **G6** The definition validator: how the JSON Schema layer and the semantic rules (19 since spec 011) are structured and reported, and the invalid fixtures (one per rule)?
- [x] **G7** The error and denial model: how a rule failure becomes an actionable denial in the driver contract, and how *not evaluated*, *failed*, and *passed* stay distinct from storage to API (Art. 7)?
- [x] **G8** The testing approach per layer: core use cases without HTTP, adapters against a real database, module-rule tests, and a replay test that rebuilds state and score from events (milestone 2)?

## Briefing

<!-- Prepared by the spike-researcher agent: what the repository and current docs already say, with sources. -->

Prepared on 2026-10-21 by the spike-researcher agent. All external facts were checked on that date.

### What the repository already settles

- **G1 Packages and module rules.** One deployable, hexagonal on both sides. The five inbound adapters, five inbound ports, eight core modules, three outbound ports, and three outbound adapters are fixed, with their responsibilities and tables (`docs/architecture/README.md` "Engine modules"; AD-1, AD-25). Dependency rules: inbound adapters depend only on inbound ports; the core only on itself and outbound ports; each outbound adapter only on its own port; no SDLC, git, or Claude Code names in the core; the broker product only in `broker-client`; `jev` only in definitions and `systemone` configuration (README "Dependency rules"). Spring annotations are allowed in the core. The rules are enforced by separate Maven modules for core, ports, and each adapter, plus ArchUnit for the name bans and the broker rule (spec 012 Q3, Q4, G8). The trial with one planted violation per rule was deferred to this spike (spec 012 Follow-ups). Open: how coarse the core's Maven modules are, which types cross the inbound ports, and where the hand-written contract records live. Inconsistency: the README `definitions` row still says 18 semantic rules; `docs/definitions/schema.md` has 19.
- **G2 Domain model.** The ER sketch and its invariants: one active run per workflow and subject key, one current stage, at most one active lease, at most one open attempt, steps strictly in order with fail-fast (`docs/concepts/model.md` "Entities", "Rules the diagrams cannot show"). Evidence is git-first: a transition request carries only the revision, and runner checks produce no evidence in the MVP (AD-27). `typed-decisions` owns walk records, provider call rows, and the input packet, stored once per tree; trees belong to the definition version (AD-29). Inconsistency: spec 008 Findings say the DecisionEngine adapters write the provider call table, but the architecture puts it in the core `typed-decisions` module. Open: the aggregate style, and whether a lease and its driver session are separate entities.
- **G3 Schema.** PostgreSQL 18, JPA / Hibernate, Flyway SQL scripts with one set per schema, `ddl-auto=validate` (spec 012 G3). `jsonb` holds the approved definition YAML and its parsed document, event payloads, and evidence payloads; everything else is ordinary tables (AD-23). A partial unique index `where status = 'active'` works for one active run per subject key (spec 012 JPA trial). Provider call columns follow the `gen_ai.*` names plus run, attempt, step, question, label, distribution, confidence, truncation, and `x-typesafe-request-id`, one row per call attempt (spec 008 G3, Q2). Operational values that affect an outcome go on the result or event they affected (README "Configuration"); since replay uses events alone, they must be in event payloads. Events carry actor, actor type, timestamp, run, definition version, correlation ID, session, evidence references, reason, the revision on each transition request, and every lease holder (README "Persistence"). Open: ID type, event ordering key, outbox table or flag, idempotency retention.
- **G4 State machines.** Lifecycles exist for stage visit and task, run, definition version, transition request and attempt, step result (including `overridden`), evaluation task, and human step request (`model.md` "Lifecycles"). Runner job states seen by the engine are `pending`, `running`, or a final outcome; cancel deletes the Job; outcomes are classified per AD-13 (README "Runner interface"). A tree walk stops when no branch matches or an answer is *not evaluated*, and each question has its own deadline (AD-29). Inconsistency: `model.md` run states are `active|completed|failed|abandoned` with `excluded` a score classification (glossary "Run score"), but `docs/driver-api/README.md` Status has `state: active|completed|failed|excluded`, and its step-state enum lacks `overridden`. Open: code or library, and explicit runner job and tree walk state lists.
- **G5 Transactions and concurrency.** Each port call is one use case and one transaction, ordered authenticate → idempotency key → lock the run row → work → events and outbox → commit (README "Inbound ports"; AD-16, AD-25). `@Lock(PESSIMISTIC_WRITE)` emits `for no key update`; lock timeout 0 gives `nowait` and -2 gives `skip locked`; concurrent claims got disjoint rows (spec 012 JPA trial, Hibernate 7.4.5). `Idempotency-Key` is an engine filter over a table. MCP keys are `hash(user, tool, canonical args)` and mutating tools echo `expected_attempts` (spec 011 Q2); hook keys are the tool use ID (gate, activity) or generated per call (register, iterations) (`docs/driver-api/README.md`). The outbox is drained at least once by `DueWork`; runner dispatch is idempotent by Job name and analysis deduplicates by event ID (README "Persistence"; AD-24). Open: external calls inside the lock, lock ordering for `DueWork`, replica count, whether denials are stored under a key.
- **G6 Validator.** Two layers: JSON Schema 2020-12 with unknown keys rejected, then 19 semantic rules; a failure in either rejects the definition and no draft is stored; YAML 1.2 (`schema.md` "Format and validation", "Semantic rules"). Jackson 3 YAML has `STRICT_DUPLICATE_DETECTION` with line and column; schema errors carry JSON pointers, mapped to lines by about 30 lines of token-stream code (spec 012 parsing trial). `submit_draft` returns `validation_failed` (422) with `details` listing each error (`docs/driver-api/README.md` Denials). `schema.md` has 12 fixtures; rules 1, 6, 7, 12, 13, 14, 15, 16, and 18 have none, and rule 5 is partly covered. Open: fail-fast or report all, and the error record shape.
- **G7 Errors and denials.** Denial shape `{code, message, recovery, details}` with 15 stable codes and HTTP statuses, sent as RFC 9457 problem+json, except the gate, which always answers 200 with `decision: deny`; the gateway maps a denial to `isError: true` (`docs/driver-api/README.md` "Denials"). Denials use Spring `ProblemDetail` (spec 012 G2). Status exposes step state `pending|running|passed|failed|not_evaluated` and `last_decision.failed_steps[].outcome failed|not_evaluated`; raw scores never reach the driver. *Not evaluated* never passes and costs no deduction; an override keeps the raw result (`schema.md` "Scoring rules"; `model.md` Step result). Open: denials as values or exceptions, and whether denials are events.
- **G8 Testing.** JUnit 5 and Testcontainers 2.0.5 with real PostgreSQL 18 and the Pub/Sub emulator through Boot service connections; contract tests validate requests and responses with networknt against the hand-written schema files (spec 012 G9, Q12, Q13). The replay check rebuilds runs, attempts, step results, decisions, leases, and scores from events and compares them with the tables; telemetry is not replayed (README "Persistence"; INTENT milestone 2). Every acceptance criterion maps to a test (Art. 11). A non-SDLC toy workflow test stays in BACKLOG "Next". Open: where the replay check runs.

### Researched

- PostgreSQL 18 lock modes: `FOR NO KEY UPDATE` conflicts with itself, `FOR SHARE`, and `FOR UPDATE`, but not `FOR KEY SHARE`. `FOR UPDATE` is also taken by `DELETE` and by an `UPDATE` of columns with a unique index usable by a foreign key; partial and expression indexes do not count ([PG 18 explicit locking](https://www.postgresql.org/docs/18/explicit-locking.html)).
- Foreign key checks on child inserts take `FOR KEY SHARE` on the parent row (long-standing RI trigger behaviour, not stated on that page). So `FOR NO KEY UPDATE` on `run` serializes use cases without blocking event or outbox inserts; `FOR UPDATE` would only be needed to delete a run or change its key. To confirm in the lock prototype.
- `SKIP LOCKED` gives "an inconsistent view… suitable for queue-like tables"; `FOR … OF alias` locks only that table's rows; `ORDER BY` with a lock under READ COMMITTED can return rows out of order ([PG 18 SELECT](https://www.postgresql.org/docs/18/sql-select.html)).
- An insert that conflicts on a unique index with an uncommitted row waits for that transaction, then fails if it committed, so two concurrent requests with the same key serialize on the key row ([PG 18 index uniqueness checks](https://www.postgresql.org/docs/18/index-unique-checks.html)).
- The Idempotency-Key draft is still -07 (2025-10-15, expired 2026-04-18): missing key → 400; same key with a different payload → 422; request still in progress → 409; a retry SHOULD return the earlier result, success or error; expiry is the resource's documented policy ([datatracker](https://datatracker.ietf.org/doc/html/draft-ietf-httpapi-idempotency-key-header)). The contract has no code for a request still in progress.
- Hibernate added Jackson 3 `FormatMapper`s in 7.3 ([ORM 7.3](https://in.relation.to/2026/04/07/orm-73/); [`Jackson3JsonFormatMapper`](https://docs.hibernate.org/orm/current/javadocs/org/hibernate/type/format/jackson/Jackson3JsonFormatMapper.html)); the selector is `hibernate.type.json_format_mapper`. Boot 4.1.1 ships Hibernate 7.4.5; which mapper the trial used was not checked.
- PostgreSQL 18 has built-in `uuidv7()`, and Hibernate 7 has `@UuidGenerator(style = VERSION_7)` ([Hibernate 7.1 javadoc](https://docs.hibernate.org/orm/7.1/javadocs/org/hibernate/annotations/UuidGenerator.Style.html)).
- Flyway lists PostgreSQL 18 as verified ([Redgate](https://documentation.red-gate.com/fd/postgresql-database-277579325.html)); Boot 4 needs `spring-boot-starter-flyway`. Flyway 12.4.0 already ran on PostgreSQL 18 in spec 012's trial.
- ArchUnit 1.4.1 reads Java 25 class files ([1.4.1 notes](https://newreleases.io/project/github/TNG/ArchUnit/release/v1.4.1)). It reads bytecode on the test classpath, so the name-ban test must sit where all core classes are visible. It sees class, package, member, and dependency names, not string literals (to confirm in the trial).
- Maven compile classpaths include transitive compile-scope dependencies, so module boundaries can leak transitively. `maven-dependency-plugin` 3.11.0 `analyze-only` with `failOnWarning=true` fails on used-but-undeclared dependencies ([docs](https://maven.apache.org/plugins/maven-dependency-plugin/analyze-only-mojo.html)).
- networknt json-schema-validator 3.0.8 (Jackson 3): `SchemaRegistry.withDialect(Dialects.getDraft202012())` → `Schema.validate(...)` → `List<Error>`, each with `instanceLocation`, `evaluationPath`, `schemaLocation`, `keyword`, `message`, `messageKey`, and `arguments`; supports classpath `$id` mapping and custom keywords ([GitHub](https://github.com/networknt/json-schema-validator)).
- Hibernate flushes in ActionQueue order (orphan removal, inserts, updates, collection actions, deletes), not call order ([5.2 guide](https://docs.jboss.org/hibernate/orm/5.2/userguide/html_single/chapters/flushing/Flushing.html); confirm for 7.x by trial). `JdbcClient` in the same Spring transaction does not flush the session; only Hibernate queries auto-flush.

## Questions for you

<!-- Prepared by the agent, highest impact first; each says which goal questions it serves, why it matters, the options, and a suggested answer. Ticked when answered or skipped. -->

- [x] **Q1** (G5, G1) Should external calls (runner dispatch and poll, Jev questions) run outside the transaction that holds the run lock? Jev calls can take up to 30 s with retries; holding the run lock that long blocks `gate` calls, which fail closed under the hook's 8 s limit. Options: claim in a short transaction, call with no lock held, apply the result in a new transaction under the run lock; or call inside one transaction. Suggested: the three-phase pattern, one transaction per due item. Refines AD-25 for `DueWork`.
- [x] **Q2** (G1) How is the core split into Maven modules? Options: one `engine-core` module with a package per core module and ArchUnit for intra-core rules and cycles; or one Maven module per core module (8). Suggested: one core module, plus separate modules for inbound ports, outbound ports, and each adapter, with `dependency:analyze-only failOnWarning`.
- [x] **Q3** (G1, G7) What types cross the inbound ports? Options: port-owned command and result records, with adapters mapping contract JSON to them; or core domain types exposed. Suggested: port-owned records, and contract records only in `driver-api`, so entities never leave the core.
- [x] **Q4** (G2, G5) Is the run one large JPA aggregate with mapped collections, or a small `run` lock root with child entities in separate repositories and no bidirectional collections? Suggested: the small root, because the run lock already gives the consistency boundary and it avoids cascade and flush surprises.
- [x] **Q5** (G5, G7) Is a denial stored under its `Idempotency-Key` and replayed on retry? Draft -07 says replay errors too, but with MCP keys of `hash(user, tool, args)` a `stage_task_exhausted` or `lease_held` denial would return forever after the cause is fixed. Suggested: store only committed results, so denials are re-evaluated.
- [x] **Q6** (G7, G3) Are denials recorded as events? Options: gate and transition denials as audit events that replay ignores; log only; nothing. Suggested: audit events for gate and `request_transition` denials (Art. 8).
- [x] **Q7** (G4, G7) Which run status set is authoritative? `model.md` has `active|completed|failed|abandoned` with `excluded` a score classification; the driver contract Status has `excluded` as a state and its step states lack `overridden`. Suggested: follow `model.md` and update the contract.
- [x] **Q8** (G3, G5) Are events published through a separate outbox table or a `published_at` column on events? A cursor over a sequence alone is unsafe because transactions commit out of order. Suggested: one outbox table for events, telemetry rows, and runner dispatch, keeping events immutable and giving `DueWork` one queue.
- [x] **Q9** (G3) Which ID scheme? IDs appear in the API, in Kubernetes Job names (lowercase DNS label, at most 63 characters), and in broker ordering keys. Options: app-generated UUIDv7; or bigint identity with a public ID column. Suggested: UUIDv7 everywhere, plus a gapless per-run `seq` on events (unique `(run_id, seq)`) assigned under the run lock.
- [x] **Q10** (G5) How many engine replicas does the MVP run? Suggested: 1, but claims designed with `FOR NO KEY UPDATE SKIP LOCKED` so that more work.
- [x] **Q11** (G6) Does the validator report every error or stop early? Suggested: structure first; if it passes, run all 19 rules and report every error with layer, rule number, JSON pointer, line and column, and message.
- [x] **Q12** (G8) Where does the replay check run? Options: tests only; tests plus an on-demand per-run check; after every finished run. Suggested: tests plus an on-demand per-run check in its own read-only transaction.
- [x] **Q13** (G3, G5) How long are idempotency records kept? Options: 24 h, 7 days, the life of the run, forever. Suggested: 7 days, purged by `DueWork`.
- [x] **Q14** (G1) Split `engine-ports-out` into one Maven module per port? With one module, only ArchUnit (late, in `engine-app`) catches an outbound adapter using another adapter's port; with three tiny modules the compiler catches it. Suggested: keep one module; ArchUnit already catches it and the ports are small.
- [x] **Q15** (G1) The banned-name rule is a case-insensitive substring match, so it flags harmless names like `digit`, `legitimate`, or `emerge`. Match whole words in camel case instead (`Git`, `Claude`, `Sdlc`, `Merge`, `PullRequest` as name parts)? Suggested: yes, word parts.
- [x] **Q16** (G1) Should the repository carry its own Maven settings (`.mvn/maven.config` pointing at a project `settings.xml` with Maven Central), so builds ignore your global settings that mirror to the corporate Nexus? Suggested: yes.
- [x] **Q17** (G1) How are adapters packaged into Maven modules? Options: one module per adapter (spike 012, 10 modules); grouped into `adapters-in` and `adapters-out` (6 modules); one module with ArchUnit for everything. Answer: grouped.
- [x] **Q18** (G1, G2) Is the core one Maven module, a pure `engine-domain` module plus `engine-core`, or one module per core module? Answer: one module, with an ArchUnit rule keeping the pure-function packages free of JPA and Spring.

## Log

<!-- Append-only, oldest first: "- YYYY-MM-DD · <source> · <what was answered, added, or found>". -->

- 2026-10-21 · research · Briefing prepared by the spike-researcher agent (see Briefing). It found three inconsistencies to settle: the README says 18 semantic rules where `schema.md` has 19; spec 008 has DecisionEngine adapters writing the provider call table where the architecture has `typed-decisions`; and the run and step state sets differ between `model.md` and the driver contract (Q7).
- 2026-10-21 · answer to Q1 · Three-phase pattern for external calls: claim the due item in a short transaction, call the runner or Jev with no lock held, apply the result in a new transaction under the run lock. One transaction per due item.
- 2026-10-21 · answer to Q2 · One `engine-core` Maven module with a package per core module, and ArchUnit for intra-core rules and cycles. Separate Maven modules for inbound ports, outbound ports, and each adapter, with `dependency:analyze-only failOnWarning`.
- 2026-10-21 · answer to Q3 · Inbound ports own their command and result records. Contract records live only in `driver-api` (and the other inbound adapters), which map them; JPA entities never leave the core.
- 2026-10-21 · answer to Q4 · Small run root: the `run` row is the lock and consistency boundary; visits, attempts, step results, and leases are separate entities with their own repositories and no bidirectional collections.
- 2026-10-21 · answer to Q5 · Only committed results are stored under an `Idempotency-Key`; a retried denial is evaluated again against current state.
- 2026-10-21 · answer to Q6 · Gate and `request_transition` denials are recorded as audit events that replay ignores; other denials are not events.
- 2026-10-21 · answer to Q7 · `model.md` is authoritative: run states `active|completed|failed|abandoned`, with `excluded` a separate score classification. The driver contract is to be updated, and `overridden` added to its step states.
- 2026-10-21 · answer to Q8 · One outbox table for events, telemetry rows, and runner dispatch; events stay immutable and `DueWork` drains one queue.
- 2026-10-21 · answer to Q9 · App-generated UUIDv7 for every engine row (Hibernate `VERSION_7`), plus a gapless per-run `seq` on events, unique on `(run_id, seq)` and assigned under the run lock, for replay order.
- 2026-10-21 · answer to Q10 · The MVP runs one engine replica, but `DueWork` claims rows with `FOR NO KEY UPDATE SKIP LOCKED` so that more replicas would also work.
- 2026-10-21 · answer to Q11 · Validator runs the JSON Schema layer first; if it passes, it runs all 19 semantic rules and reports every error with layer, rule number, JSON pointer, line and column, and message.
- 2026-10-21 · answer to Q12 · Replay check runs in tests and as an on-demand per-run check in its own read-only transaction.
- 2026-10-21 · answer to Q13 · Idempotency records are kept 7 days and purged by `DueWork`.
- 2026-10-21 · note from the user · No preference on which prototypes to run; left to the agent.
- 2026-10-22 · research · Drafted `docs/engine/design.md` (sections 1–10) and `docs/engine/schema-draft.sql` from the decisions so far. Agent proposals in it, to agree in the recommendation: a `usecases` package implementing the inbound ports and doing all outbound calls, which removes the `outbox` ↔ `checkpoints` cycle; denials as `Outcome` values; enum state tables with no library; no `transition_request` table; idempotency inside the core with denial rows deleted before commit and no `409` for in-progress retries; no `@Version` columns; ReplayCheck as a core service on pure fold functions. Prototypes `module-rules` and `locks` started; both were interrupted several times by the Mac sleeping.
- 2026-10-22 · prototype · Module-rule trial (`prototype/module-rules/`, Java 25.0.3, Maven 3.9.16, Boot 4.1.1, ArchUnit 1.5.1, dependency plugin 3.11.0): every planted violation was caught. The compiler catches imports across modules; ArchUnit in `engine-app` catches them once a pom adds the forbidden dependency, plus banned names (classes, packages, members), the Pub/Sub rule, core cycles, and JPA types in ports; `analyze-only` catches transitive leaks. Not caught: string literals (expected), and with one `engine-ports-out` module only ArchUnit catches an outbound adapter using another port. Findings: `engine-app` needs `ignoredUnusedDeclaredDependencies` for sibling modules (wired by component scan); Spring Boot starters and the `archunit-junit5` aggregator cannot be used with `analyze-only failOnWarning`, so modules declare the real jars; the banned-name rule is a substring match and flags names like `digit` or `emerge`; the global `~/.m2/settings.xml` mirrors everything to a corporate Nexus, so the project needs its own Maven settings (the prototype has `maven-settings.xml` and runs offline).
- 2026-10-23 · answer to Q14 · Keep one `engine-ports-out` module; ArchUnit catches an outbound adapter using another port.
- 2026-10-23 · answer to Q15 · The banned-name rule matches camel-case word parts (`Git`, `Claude`, `Sdlc`, `Merge`, `PullRequest`), not substrings.
- 2026-10-23 · answer to Q16 · The repository carries its own Maven settings (`.mvn/maven.config` plus a project `settings.xml` pointing at Maven Central), so builds ignore the global settings that mirror to the corporate Nexus.
- 2026-10-23 · prototype · Lock and idempotency trial (`prototype/locks/`, Boot 4.1.1, Hibernate 7.4.5, PostgreSQL 18, 21 tests green twice): `@Lock(PESSIMISTIC_WRITE)` emits `for no key update of r1_0` (spec 012's `jpa-trial` comment saying `FOR UPDATE` is wrong); child inserts under it take 2–3 ms while `FOR UPDATE` blocks them; updating `status` or `subject_key` keeps "No Key Update" and `one_active_run` still enforces; only a PK change or a run delete escalates. Deadline-then-run against run-then-deadline deadlocks (`40P01`); the three-phase claim had 0 deadlocks in 20 racing rounds. Same idempotency key: `INSERT … ON CONFLICT DO NOTHING` waits, then replays; after a rollback the waiter runs; a plain `INSERT` aborts the transaction (`25P02`). `JdbcClient` never flushes; JPQL flushes only for overlapping tables, any native query through the `EntityManager` flushes the whole session; mixed JDBC and JPA writes gave a false replay mismatch. Gapless `seq` under the run lock (8 threads × 25). Hibernate picks `Jackson3JsonFormatMapper` but with its own mapper, writing `Instant` as epoch seconds; passing Boot's `JsonMapper` through a `HibernatePropertiesCustomizer` writes ISO-8601. The prototype added `deadline.claimed_until`.
- 2026-10-27 · answer to Q17 · Adapters are grouped into two Maven modules, `adapters-in` and `adapters-out`, each adapter a package; six modules in total. Refines spike 012's module per adapter. The user asked why adapters need their own modules; the main reason kept is that the core cannot see adapter libraries. ArchUnit now also checks that adapter packages do not use each other.
- 2026-10-27 · answer to Q18 · The user asked why the core is one module. Kept as one module: the core's packages share one stack, one persistence unit, and one transaction per use case, so separate modules would isolate no libraries; ArchUnit already guards calls between them. Added: pure rule code lives in `rules` subpackages, and an ArchUnit rule keeps them free of JPA, Spring, and entities. A separate `engine-domain` module and eight core modules were rejected.
- 2026-10-27 · note from the user · Asked to run `/sdd-verify`, taken as agreement to the recommendation and follow-ups.

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1

- **Decision** (Q2): one `engine-core` Maven module with a package per core module; ArchUnit checks intra-core rules and cycles. Inbound ports, outbound ports, and each adapter are separate Maven modules, and `maven-dependency-plugin` 3.11.0 `analyze-only` with `failOnWarning=true` catches transitive leaks.
- **Decision** (Q3): inbound ports own their command and result records; contract records live only in the inbound adapters, which map them. JPA entities never cross a port.
- **Fact** (spec 012; ArchUnit 1.4.1): ArchUnit reads Java 25 bytecode on the test classpath, so the name-ban test sits where all core classes are visible.
- **Fact** (module-rule trial, `prototype/module-rules/README.md`): every rule is enforced. The compiler catches cross-module imports; ArchUnit 1.5.1 in `engine-app` catches violations that come with a pom change, banned class, package, and member names, the Pub/Sub rule, core cycles, and JPA types in ports; `analyze-only` catches transitive leaks. String literals are not checked.
- **Fact** (same): `analyze-only failOnWarning` rules out Spring Boot starters and the `archunit-junit5` aggregator; modules declare real artifacts. `engine-app` ignores unused declared sibling modules. The project needs its own Maven settings, because the user's global settings mirror to a corporate Nexus.
- **Proposal** (design §1): a ninth core package, `usecases`, implements the inbound ports (one class per port) and is the only place that calls outbound ports, in `DueWork`'s no-lock phase. `outbox` only stores items and answers what is due, which removes the `outbox` → `checkpoints` cycle in the architecture. Allowed calls between core packages are tabled in design §1 and checked by ArchUnit layered and cycle rules in `engine-app`.
- **Decision** (Q14): one `engine-ports-out` module.
- **Decision** (Q15): banned names match camel-case word parts, not substrings.
- **Decision** (Q16): the repository carries its own Maven settings pointing at Maven Central.
- **Decision** (Q17): six Maven modules: `engine-ports-in`, `engine-ports-out`, `engine-core`, `adapters-in`, `adapters-out`, `engine-app`. Each adapter is a package in its group. ArchUnit checks that adapter packages do not use each other and that each outbound adapter uses only its own port.
- **Decision** (Q18): the core stays one Maven module; pure rule code sits in `rules` subpackages, kept free of JPA and Spring by ArchUnit.

### G2

- **Decision** (Q4): the `run` row is the aggregate root, lock, and consistency boundary. Visits, attempts, step results, and leases are separate entities with their own repositories and no bidirectional collections; each use case reads what it needs explicitly.
- **Proposal** (design §3): entities per module and invariants are tabled in design §3. Lease and driver session are separate entities. There is no transition request table: a guarded request is a `CheckpointAttempt`, an unguarded one is its events, a denied one an audit event. Computing rules (pass conditions, median, branches, scoring) are pure functions on records, shared with replay. Definitions are read as cached immutable `DefinitionModel` records.

### G3
- **Decision** (Q8): one outbox table holds pending broker events, telemetry rows, and runner dispatches; the event table stays append-only and immutable.
- **Decision** (Q6): gate and `request_transition` denials are written as audit events, marked so that replay skips them.
- **Decision** (Q9): every engine row has an app-generated UUIDv7 key (Hibernate `@UuidGenerator(style = VERSION_7)`); events also carry a gapless per-run `seq`, unique on `(run_id, seq)` and assigned under the run lock.
- **Decision** (Q13): idempotency records are kept 7 days and purged by `DueWork`.
- **Proposal** (design §4, `schema-draft.sql`): full draft DDL per core module; lifecycle columns are text with check constraints matching the enums; outcome-affecting settings go in event payloads and a `settings` column; event payloads per type are tabled in design §4; `event.position` (identity) only pages `EventFeed`.
- **Fact** (lock trial): Hibernate 7.4.5 picks `Jackson3JsonFormatMapper` with its own mapper and writes `Instant` as epoch seconds; Boot's `JsonMapper` passed through a `HibernatePropertiesCustomizer` writes ISO-8601. Design §4 uses the customizer.
- **Fact** (lock trial): `deadline` needs `claimed_until` and `claim_token` for the claim lease; added to `schema-draft.sql`.

### G4
- **Decision** (Q7): run states are `active|completed|failed|abandoned` (`docs/concepts/model.md`); `excluded` is a separate score classification. Follow-up: update `docs/driver-api/README.md` Status (run `state`, and `overridden` in step states).
- **Proposal** (design §5): each lifecycle is an enum in `engine-ports-in` with an explicit move table; entities change state only through a guarded `transitionTo`; an illegal move is a bug (exception), never a denial; no state machine library. New diagrams for the runner job and the tree walk; guards tabled in design §5.

### G5

- **Decision** (Q1): use cases that call the runner or Jev use three phases: claim the due item in a short transaction, call with no lock held, apply the result in a new transaction under the run lock. One transaction per due item. This refines AD-25 ("one transaction per port call") for `DueWork`.
- **Fact** (lock trial; PG 18 docs): `@Lock(PESSIMISTIC_WRITE)` emits `for no key update`; it serializes use cases on one run (B waited 803–807 ms) without blocking child inserts (2–3 ms), while `FOR UPDATE` blocks them. Updating `status` keeps the weaker lock and `one_active_run` still enforces. No use case needs `FOR UPDATE`.
- **Fact** (lock trial): locking a deadline before its run deadlocks against the run-first order; the three-phase claim had 0 deadlocks in 20 racing rounds. Lock order is idempotency row, run, child rows.
- **Fact** (lock trial): same-key requests serialize on `INSERT … ON CONFLICT DO NOTHING` and the waiter replays the stored response, or runs after a rollback; a plain `INSERT` aborts the transaction. `JdbcClient` never flushes the session, any `EntityManager` native query flushes all of it, and mixed JDBC and JPA writes fool the replay check, so use cases write through JPA only. Gapless `seq` from `run.next_event_seq` holds under concurrency.
- **Assumption**: deleting a denial's idempotency row before commit behaves like the tested rollback for the waiter (the row never becomes visible).
- **Decision** (Q5): the idempotency table stores only committed results. A retried request whose first attempt was denied is evaluated again.
- **Decision** (Q10): one engine replica in the MVP; `DueWork` claims due rows with `FOR NO KEY UPDATE SKIP LOCKED`, so more replicas stay safe.
- **Decision** (Q13): idempotency records expire after 7 days.

### G6
- **Decision** (Q11): structure first (JSON Schema 2020-12, networknt 3.0.8); only if it passes, all 19 semantic rules run. Every error is reported with layer, rule number, JSON pointer, line and column, and message, inside the `validation_failed` denial's `details`.
- **Proposal** (design §7): `DefinitionParser` → `StructureValidator` → `DefinitionModel` → 19 `SemanticRule` classes; `ValidationError(layer, rule, keyword, pointer, line, column, message)`; the schema file at `definitions/schema/ai-workflow-v0.schema.json`, mapped by `$id` from the classpath; one fixture per rule (rules 1, 6, 7, 12, 13, 14, 15, 16, 18 and a duplicate key are new).

### G7
- **Decision** (Q5): denials are never replayed from the idempotency table; they are evaluated against current state.
- **Decision** (Q6): gate and `request_transition` denials become audit events that replay ignores; other denials are only returned (and logged).
- **Decision** (Q7): Status gains `overridden` as a step state, and run `state` follows `model.md`.
- **Proposal** (design §8): denials are `Outcome.Denied` values, never exceptions; `driver-api` maps `DenialCode` to status and `ProblemDetail`; unexpected failures are 500 or 503 with no code and fail closed; `NOT_EVALUATED` is kept distinct in enum, check constraint, payload, and API, with exhaustive switches and no `default`.

### G8
- **Decision** (Q12): the replay check runs in tests (milestone 2) and as an on-demand check of one run in its own read-only transaction.
- **Proposal** (design §9): test layers tabled in design §9: pure rules, validator, core use cases through the ports with Testcontainers PostgreSQL 18 and in-memory fakes of the outbound ports, concurrency, adapters (MockMvc plus contract schema validation; WireMock; Pub/Sub emulator), module rules, and scenario tests T01, T05, T06, T07 each followed by `ReplayCheck`.

## Recommendation and decisions

<!-- Drafted when every goal question is answered, and agreed with the user. -->

| Decision | Choice | Alternatives considered | Why |
| --- | --- | --- | --- |
| Maven modules | Six: `engine-ports-in`, `engine-ports-out`, `engine-core` (a package per core module plus `usecases`), `adapters-in`, `adapters-out` (a package per adapter), `engine-app` | Eight core modules; a pure `engine-domain` module; one module per adapter (spike 012); one module per outbound port; one module for everything | Q2, Q14, Q17, Q18: the compiler keeps adapters and their libraries away from the core; ArchUnit guards the core and adapter packages; fewer POMs |
| Rule enforcement | Maven module boundaries, `dependency:analyze-only failOnWarning`, ArchUnit 1.5.1 in `engine-app` (layers, cycles, names as camel-case word parts, Pub/Sub, JPA in ports) | ArchUnit only; substring name match | Trial caught every planted violation; Q15 avoids false hits |
| Build settings | Project Maven settings to Maven Central; JDK 25 pinned; real artifacts, no starters | Global settings; starters | Q16; global settings stall on the corporate Nexus; starters break `analyze-only` |
| Port types | Port-owned records and sealed `Outcome`; contract records only in adapters | Core types in ports | Q3: entities never leave the core |
| Calls between core modules | `outbox` only stores items; `usecases` calls outbound ports and routes results | `outbox` calls ports and `checkpoints` (architecture) | Removes a cycle and keeps external calls out of transactions |
| Aggregate | Small `run` root as lock and consistency boundary; children separate, no collections or cascades | One large JPA aggregate | Q4: explicit reads, no flush surprises |
| Transition requests | No table: an attempt row, or events, or an audit event | A `transition_request` table | Nothing reads a request row that an attempt or event does not already hold |
| External calls | Three phases per due item: claim (`SKIP LOCKED`, claim lease), call with no lock, apply under the run lock | Call inside the run-lock transaction | Q1: Jev's 30 s would stall gates past 8 s; trial: 0 deadlocks |
| Run lock | `@Lock(PESSIMISTIC_WRITE)` = `FOR NO KEY UPDATE`; order key → run → children | `FOR UPDATE` | Trial: serializes use cases without blocking child inserts |
| Replicas | One, with multi-replica-safe claims | Plain select | Q10 |
| IDs and order | UUIDv7 everywhere; gapless per-run `seq` from `run.next_event_seq`; global `position` for paging | bigint identity | Q9; fits Job names; trial: gapless under concurrency |
| Outbox | One table for dispatch, cancel, questions, events, telemetry | `published_at` on events | Q8: immutable events, one queue |
| Idempotency | In the core: `INSERT … ON CONFLICT DO NOTHING` first; committed results only; denial rows deleted before commit; waiters wait, no `409`; 7 days | A servlet filter; store denials (draft -07) | Q5, Q13; adapters may not touch storage; trial confirmed the waiting |
| Denials | `Outcome.Denied` values; gate and transition denials as audit events replay skips | Exceptions; no audit | Q6; the compiler forces handling; Art. 8 |
| Run states | `active / completed / failed / abandoned` plus a classification; `overridden` step state in the contract | `excluded` as a state | Q7: follows `model.md` |
| State machines | Enum move tables in `engine-ports-in`, guarded `transitionTo`, exhaustive switches | A state machine library | Small machines; compile errors on new states |
| JPA writes | JPA only inside a use case; `JdbcClient` only before entity changes; no `@Version`; jsonb through Boot's `JsonMapper` | Mixed JDBC and JPA; optimistic locks | Trial: no auto-flush guard, mixed writes fool replay; Hibernate's default mapper writes `Instant` as numbers |
| Validator | Parse → structure → all 19 semantic rules; full error list with layer, rule, pointer, line, column; one fixture per rule | Stop at first error | Q11 |
| Testing and replay | Layers in design §9; `ReplayCheck` core service in tests and on demand per run | Tests only | Q12 |

## Deliverables

- [x] Every goal question answered, and the recommendation agreed with the user
- [x] Low-level design document (`docs/engine/design.md`) with package or class diagrams, a draft schema DDL, and state machine diagrams
- [x] BACKLOG.md items settled by this spike updated

## Follow-ups

<!-- New specs, BACKLOG items, experiments, or open questions this spike created. -->

Things to try (proposed by the researcher; prototypes go under `prototype/`):

- **Module-rule trial** (about half a day). Skeleton reactor with `engine-ports-in`, `engine-core`, `engine-ports-out`, `driver-api`, `scheduler`, `systemone`, `broker-client`, `engine-app`, and one planted violation per rule: adapter imports core, core imports adapter, one outbound adapter imports another's port, a transitive leak (`analyze-only`), a `git` or `claude` name in the core (ArchUnit), `com.google.cloud.pubsub` outside `broker-client`. Record which tool catches each.
- **Lock and idempotency prototype** (about half a day, extending spec 012's `jpa-trial`): two use cases on one run serialize; event and outbox FK inserts are not blocked by `FOR NO KEY UPDATE`; reproduce and fix a `DueWork` deadline-row versus run-lock deadlock with run-first ordering; two concurrent requests with one key; `JdbcClient` does not see an unflushed entity change.
- **Jackson 3 mapper check** (about 1 hour): which `FormatMapper` Hibernate 7.4.5 uses, and a record mapped to `jsonb` through `hibernate.type.json_format_mapper`.
- **Validator slice** (about 2 hours): rules 3, 10, and 17 as `SemanticRule` classes with the pointer-to-line mapping, on three fixtures.
- **Mini replay** (about half a day): rebuild one run's stage, attempt, step results, and score from events in a read-only transaction and diff against the tables.

After agreement (applied by `/sdd-verify 013` on 2026-10-27; where each one went is noted):

- Edit `docs/driver-api/README.md` (run state and classification, `overridden` step state), `docs/architecture/README.md` (19 rules, `usecases` calls outbound ports), and `docs/architecture/decisions.md` (AD-25 three-phase `DueWork`, AD-16 idempotency), as listed in design §10. *Done*, plus a note under the architecture's engine module diagram.
- Fix the comment in spec 012's `prototype/hello-world/jpa-trial` that says `PESSIMISTIC_WRITE` emits `FOR UPDATE`. *Done.*
- Phase 0 scaffolding: the six-module layout, project Maven settings, JDK 25 pin, and ArchUnit rules from `prototype/module-rules/`. *In PLAN Phase 0 "Engine skeleton"; build settings also in BACKLOG spike 10.*
- Milestone 1: write the nine missing invalid fixtures (rules 1, 6, 7, 12, 13, 14, 15, 16, 18) and a duplicate-key fixture. *In PLAN Phase 1 "Definition model".*
- Confirm in Phase 1 that deleting a denial's idempotency row before commit behaves like the tested rollback for a waiting retry. *In PLAN Phase 1 "Driver API".*
- Not run (low value now): the validator slice and the mini replay; both become Phase 1 tests. *In PLAN Phase 1 "Engine core" (scenario tests with the replay check) and "Definition model".*

## Changelog

- 2026-10-21 Created.
- 2026-10-21 Briefing and questions Q1–Q13 prepared; status in-progress.
- 2026-10-23 Prototypes `module-rules` and `locks` run; Q14–Q16 answered; design drafted in `docs/engine/`; all goal questions answered.
- 2026-10-27 Q17 answered: adapters grouped into two Maven modules; design §1 updated.
- 2026-10-27 Q18 answered: core stays one module, with a purity rule for `rules` subpackages.
- 2026-10-27 Recommendation agreed; spike released for verification.
- 2026-10-27 Verified and closed: every goal question answered, recommendation agreed, follow-ups placed in PLAN and BACKLOG, design §10 edits applied to `docs/driver-api/`, `docs/architecture/`, and spec 012's prototype comment.
