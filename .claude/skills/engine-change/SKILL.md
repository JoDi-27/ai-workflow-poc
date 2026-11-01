---
name: engine-change
description: Steps and judgment calls for changing the engine's hexagonal code (engine/): adding or changing an inbound operation, an outbound port and its adapter, a migration, or a pure rule, and picking the right test layer. Use when planning or implementing any task that touches engine/.
argument-hint: <what you are changing, e.g. "add driver operation report_iteration">
---

# Engine change

Change: $ARGUMENTS

The engine is hexagonal: inbound adapters → inbound ports → core → outbound ports → outbound adapters. Maven modules and ArchUnit enforce the boundaries, so this skill does not repeat them; a wrong dependency fails the build. This skill holds what the build cannot tell you: the order of the steps, where each piece goes, and the calls that need judgment. The facts live in `docs/engine/design.md` (here "design §N"); read the section a step names before doing it.

**Worked example:** the stub `status` (spec 017) went through every layer of an inbound operation. Copy its shape:

| Step | File |
| --- | --- |
| Port records and method | `engine/engine-ports-in/src/main/java/aiworkflow/engine/port/in/` (`StatusQuery`, `StatusView`, `DriverOperations`) |
| Use case | `engine/engine-core/src/main/java/aiworkflow/engine/core/usecases/DriverOperationsService.java` |
| Adapter and denial mapping | `engine/adapters-in/src/main/java/aiworkflow/engine/adapter/in/driverapi/` (`StatusController`, `StatusResponse`, `Denials`) |
| Tests | each module's `src/test/`, and `engine/engine-app/src/test/.../EngineIntegrationTest.java` |

Commands, gates, and the plants are in `engine/README.md`. Run Maven from `engine/`.

## Before you start

1. **Should it be in the engine at all?** The engine is neutral (article 3). If the change carries SDLC, git, or provider meaning, it belongs in the workflow definition (`docs/definitions/`) or a driver kit, and the engine only gets a generic mechanism. ArchUnit catches names like `PullRequest`; it does not catch a field called `approved` that only makes sense for code review.
2. **Is the design still right?** If the change contradicts the design or `schema-draft.sql`, update the document and the spec's plan first (article 13).

## Add an inbound operation

For a driver, UI, telemetry, or event-feed operation. Design §2 (ports), §6 (use-case frame), §8 (errors).

1. **Port records** in `engine-ports-in`: the command and result records. Plain Java records and sealed interfaces, no JPA, Jackson, or Spring types. Reuse the lifecycle enums that already live there.
2. **Port method** on the inbound port (`DriverOperations`, `HumanOperations`, …). It returns `Outcome<T>`, takes `Caller` and, if it changes state, `Idempotency`.
3. **Use case** in the core's `usecases` package, in the frame from design §6: authenticate → idempotency (mutating calls only) → lock the run → load and check → write state and events → commit. A broken rule returns `Outcome.Denied` with a `DenialCode`; never throw for it.
4. **Module service** in the core module that owns the rule (design §1 lists who may call whom). Put a rule that needs no state in a `rules` subpackage (below).
5. **Inbound adapter** package in `adapters-in`: the hand-written contract record with snake_case names (`@JsonProperty`), the mapping to and from the port records, and `Outcome.Denied` through `driverapi.Denials`, which already maps every `DenialCode` to the contract's status and problem body. A malformed request is an `invalid_inputs` denial. No authorization logic here; the core authenticates.
6. **Contract and MCP mirror:** update `docs/driver-api/` (operation, schema, denials) and the matching MCP tool in the gateway (`mcp-gateway/`, once the MCP gateway skeleton lands).
7. **Events:** if the use case changes run state, append the event and make sure its payload has what replay needs (design §4, event payload table).
8. **Tests:** a core use-case test through the port, with fakes (plain JUnit while the use case touches no state); a standalone MockMvc adapter test with a fake port, validated against the JSON Schema once the contract files exist; an `engine-app` test over real HTTP named `*IntegrationTest`, which PIT leaves out; if it changes run state, a scenario step and the replay check (design §9).

## Add an outbound port and its adapter

For a new external system the core needs. Design §2, §6 ("External calls: three phases").

1. **Port** in `engine-ports-out`: the interface and its records, in generic terms (`DecisionEngine`, not `JevClient`). Calls must be safe to repeat.
2. **In-memory fake** for core tests, in the same task as the port.
3. **Contract tests** that state what the port promises, including errors, timeouts, and *not evaluated*, run against the fake and every real adapter. (Pending a spec that amends design §9; follow that spec once it lands.)
4. **Adapter** package in `adapters-out`, implementing only its own port. It returns results; it never writes to storage.
5. **Calling it:** only `usecases` calls an outbound port, and only in `DueWork`'s no-lock phase. A use case that needs the call writes an outbox item; the result comes back through a normal use-case frame.
6. **Tests:** the adapter against recorded responses (WireMock) or an emulator in Testcontainers (design §9).

## Change a port

- Change the fake and the contract tests in the same task as the port.
- A lifecycle enum change also changes the check constraint in a migration, and every exhaustive `switch` over it (design §4, §8).

## Add a migration

Design §4.

- Flyway SQL in `engine-app` (`src/main/resources/db/migration/engine`), forward-only, no Java migrations. Flyway owns the `engine` schema and its history table. Once JPA arrives with the first entity, startup also validates the schema (`ddl-auto=validate`).
- Update `schema-draft.sql` or the design first, then write the migration.
- A changed lifecycle column ships with its enum change.

## Add a pure rule

Design §1 ("Pure rule code stays pure"), §9.

- Goes in a `core..rules` subpackage: state move tables, pass conditions, scoring, and similar. No JPA, Spring, or entity types.
- Test it with plain JUnit 5, no Spring and no database.

## Pick the test layer

Use the table in design §9: pure rules → plain JUnit; core use cases → Spring context with Testcontainers PostgreSQL and fakes; inbound adapters → MockMvc against the contract schemas; outbound adapters → WireMock or an emulator; scenarios → scripted port calls plus the replay check. Each acceptance criterion maps to one of these (article 11).

## Dependencies

- Declare real artifacts, not starters: `analyze-only` fails on a used but undeclared dependency in any scope, and on an unused compile-scope one (design §1).
- A library the app needs at run time stays run time scope even when only a test uses it. A direct test-scope declaration wins over the transitive one and drops it from the jar, while every test still passes. The CI smoke step on the packaged jar catches that.
- A new module rule in `ArchitectureTest` gets a plant in `engine/architecture-plants/` that proves it fails.

## Judgment calls the build cannot check

- No outbound call while a run is locked or a transaction is open (design §6).
- No `JdbcClient` statement after an entity change in a use case (design §6, "JPA rules").
- A switch over `StepState`, `RunnerOutcome`, or another lifecycle enum is exhaustive with no `default`, so a new state is a compile error (design §8).
- *Not evaluated*, *failed*, and *passed* stay distinct all the way to the API and the score (article 7).
- An unreachable dependency fails closed (article 5).
