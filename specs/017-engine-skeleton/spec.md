---
id: 017
title: "Engine skeleton"
type: feature
status: done
phase: 0
depends_on: [015, 016]
refs: ["PLAN Phase 0 / Scaffolding / Engine skeleton", "BACKLOG Engine framework", "BACKLOG Database"]
updated: 2026-11-01
---

# 017 · Engine skeleton

## Problem

The engine is the system's sole authority (Art. 1), and every other component talks to it. Spikes 012 and 013 settled its stack, its module layout, and the rules that keep the core neutral and free of adapter code (`docs/engine/design.md` section 1). The `engine/` folder (spec 016) holds only a README.

Before any workflow logic is written, the engine needs to exist as a buildable, startable, tested service whose module boundaries are enforced from the first commit, with its build and quality checks required in CI. Then the Phase 1 engine specs add behaviour inside a structure that cannot drift, and the other skeletons (MCP gateway, UI, driver kit, runner, manifests) have a real engine to connect to.

It moves PLAN Phase 0 scaffolding forward and serves the Phase 0 exit criteria: a Claude Code session calls the stub `status` (through the MCP gateway skeleton, Q1), and its session-start hook reaches the engine.

## Scope

**In:**

- The engine's build, with the six modules and the dependency rules from `docs/engine/design.md` section 1, enforced by the build itself.
- The repository's own build settings and toolchain pin, so the build does not depend on the machine's defaults (design section 1, "Build rules the trial found").
- Starting the engine locally against its database, with the engine's schema created and versioned by migrations, even though it holds no tables yet.
- A health endpoint that reports whether the engine and its database are reachable.
- A stub `status` operation on the engine's HTTP driver API, answering in the driver API's Status shape with no bound run and no startable workflows (Q2), and no authentication yet (Q3).
- Required CI checks for the engine: build, tests including the module rules, coverage, and mutation testing (Q4).
- The build, test, and run commands in `CLAUDE.md` and `engine/README.md`.
- A new PLAN Phase 0 scaffolding item, "MCP gateway skeleton", and the Engine skeleton item reworded to say the stub `status` is on the HTTP driver API (Q1).

**Out:**

- The MCP gateway and the MCP `status` tool: the new PLAN item "MCP gateway skeleton", its own spec (Q1).
- Driver-token authentication and every other driver API operation: PLAN Phase 1, Driver API.
- Runs, state, events, definitions, and any tables beyond what migrations need to run: PLAN Phase 1, Engine core and Definition model.
- The DecisionEngine stub adapter: PLAN Phase 0, DecisionEngine stub.
- The engine's container image and its Kubernetes manifests: PLAN Phase 0, Kubernetes manifests.
- Contract JSON Schema files and contract tests against them: PLAN Phase 1, Driver API.

## User scenarios

### S1 · A developer builds and runs the engine

A developer on a fresh clone wants to build the engine and see it running.

1. They run the documented build command; it uses the pinned toolchain and the repository's build settings, and runs every test.
2. They start the local database and run the documented start command.
3. The engine starts, applies its migrations, and its health endpoint reports it and the database as up.

### S2 · A driver reaches the engine

The MCP gateway skeleton, or a hook script, calls the engine's `status` with a session ID.

1. The engine answers with a Status in the driver API's shape: the contract version, the session, no bound run, no startable workflows.

### S3 · A later spec breaks a module rule

A Phase 1 change makes the core use an adapter library, or names a git concept in the core.

1. The build fails and names the broken rule.
2. The pull request cannot merge, because the engine check is required.

### S4 · A later spec lowers test quality

A change adds engine code with no tests, so coverage or the mutation score drops below the threshold.

1. The engine check fails and the pull request cannot merge.

## Requirements

- **R1** The engine shall be built as one deployable from the six modules named in `docs/engine/design.md` section 1, with the dependencies between them that the design's module table allows.
- **R2** If engine code breaks one of the rules in design section 1, "How the rules are checked", then the engine build shall fail.
- **R3** The engine build shall use the repository's own build settings and the toolchain version pinned in the repository, whatever the machine's default.
- **R4** When the engine starts with its database reachable, it shall apply its pending schema migrations to its own schema before serving requests.
- **R5** The engine shall expose a health endpoint that reports the engine's state and its database's reachability.
- **R6** If the database is unreachable, then the health endpoint shall report the engine as not healthy (Art. 5).
- **R7** When a caller requests `status` with a session ID on the HTTP driver API, the engine shall answer in the driver API's Status shape, with the contract version, that session, no bound run, and no startable workflows.
- **R8** The engine's tests shall include one that runs against a real database of the version the stack names.
- **R9** If the engine's line coverage falls below 80%, then the engine CI check shall fail.
- **R10** If the engine's mutation score falls below 70%, then the engine CI check shall fail.
- **R11** The engine CI check (build, tests, module rules, coverage, mutation testing) shall be a required check for merging into `main`.
- **R12** `CLAUDE.md` and `engine/README.md` shall give the commands to build, test, and run the engine locally.
- **R13** `PLAN.md` shall list an "MCP gateway skeleton" scaffolding item, and its Engine skeleton item shall say the stub `status` is on the HTTP driver API.

## Acceptance criteria

- **AC-1** Given a fresh clone, when the documented build command runs, then all six modules build and every test passes; and each module depends only on the modules design section 1 allows. (R1)
- **AC-2** Given one planted violation per row of design section 1, "How the rules are checked", when the build runs, then each fails the build at the tool the design names; without the plant, the build passes. (R2)
- **AC-3** Given a machine whose default toolchain is a different version, or whose user build settings point elsewhere, when the documented build command runs, then it uses the pinned version and the repository's settings. (R3)
- **AC-4** Given an empty database, when the engine starts, then its migrations are applied to the engine's schema and recorded as applied; on a second start, nothing is applied again. (R4)
- **AC-5** Given a running engine with its database up, when the health endpoint is called, then it reports the engine and the database as up. (R5)
- **AC-6** Given a running engine whose database becomes unreachable, when the health endpoint is called, then it reports the engine as not healthy. (R6)
- **AC-7** Given a running engine, when `status` is called on the HTTP driver API with a session ID, then the answer has the contract version, that session ID with no bound run, no run, and an empty list of startable workflows, with field names as in `docs/driver-api/README.md`. (R7)
- **AC-8** Given the engine's test suite, when it runs, then at least one test runs against a real database of the stack's version. (R8)
- **AC-9** Given a pull request whose engine code drops line coverage below 80%, when its checks run, then the engine check fails; at or above it, it passes. (R9)
- **AC-10** Given a pull request whose engine code drops the mutation score below 70%, when its checks run, then the engine check fails; at or above it, it passes. (R10)
- **AC-11** Given `main`'s merge rules, when the required checks are listed, then the engine check is one of them, and the pull request that lands this spec passes every required check. (R11)
- **AC-12** Given `CLAUDE.md` and `engine/README.md`, when a developer follows their commands on a fresh clone, then the engine builds, its tests pass, and it starts with a healthy health endpoint. (R12)
- **AC-13** Given `PLAN.md`, when it is read, then it has an "MCP gateway skeleton" scaffolding item, and the Engine skeleton item says the stub `status` is on the HTTP driver API. (R13)

## Open questions

- Q1: The engine serves HTTP only and MCP lives in a separate gateway (spike 010 Q20), but no PLAN item builds the gateway. Where does the MCP `status` come from? A: The engine exposes a stub `status` on its HTTP driver API; a new PLAN item and spec, "MCP gateway skeleton", mirrors it as an MCP tool. (owner, 2026-11-01)
- Q2: What should the stub `status` return? A: A fixed answer in the driver API's Status shape: contract version, the session, no bound run, no startable workflows. (owner, 2026-11-01)
- Q3: Should the stub `status` require a driver token? A: No; driver-token auth belongs to the Phase 1 Driver API item. (owner, 2026-11-01)
- Q4: Which engine CI checks does this spec add? A: All four now, as required: build, tests with module rules, coverage, and mutation testing. (owner, 2026-11-01)
- Q5: What coverage and mutation thresholds should the engine check require? A: At least 80% line coverage and a 70% mutation score (R9, R10). (owner, 2026-11-01)

## Changelog

- 2026-11-01 Created.
- 2026-11-01 Q1–Q4 answered: HTTP stub `status` with a separate MCP gateway skeleton item (R13), Status-shaped answer with no run (R7), no auth yet, and coverage and mutation checks now (R9–R11). Q5 (thresholds) open.
- 2026-11-01 Q5 answered: 80% line coverage, 70% mutation score (R9, R10, AC-9, AC-10).
- 2026-11-01 Approved (`/sdd-plan`).
- 2026-11-01 Planned: `plan.md` and `tasks.md` (12 tasks).
- 2026-11-01 Implementation started (`/sdd-implement`).
- 2026-11-01 Verified (`/sdd-verify`): AC-1 to AC-13 pass, evidence in `tasks.md`. Done.
