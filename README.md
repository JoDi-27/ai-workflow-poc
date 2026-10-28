# ai-workflow-poc

A proof of concept of a governed workflow engine for coding-agent drivers. The engine is the single authority for workflow definitions, runs, state, checkpoints, and scoring. A separate UI handles human decisions and monitoring. Drivers are interactive coding agents on developer laptops (Claude Code first) that reach the engine through MCP and HTTP, and a runner next to the engine validates gates in throwaway sandboxes. The reference SDLC workflow only exists to exercise the framework; the framework is the product.

## Status

Phase 0: the twelve spikes are done (named by spec ID, 001 to 014) and scaffolding is next. There is no product code yet. `PLAN.md` has the phases, `BACKLOG.md` the decisions and the checks carried over from the spikes, and `specs/STATUS.md` the progress board.

## Read first

| File | What it holds |
| --- | --- |
| `docs/README.md` | Index of the design documents, and which one to read for a given task |
| `docs/intent.md` | Product intent, concepts, and design guardrails |
| `PLAN.md` | Phases, checklists, and exit criteria |
| `BACKLOG.md` | The happy path, MVP decisions, a summary of each spike, carried-over checks, and later work |
| `docs/architecture/` | Components, engine modules, decisions, and sequences (spike 010) |
| `docs/driver-api/` | The driver contract over HTTP and MCP (spike 011) |
| `docs/engine/` | Engine low-level design and the draft schema (spike 013) |
| `docs/definitions/` | The definition schema and the reference workflow (spike 003) |
| `docs/local-setup/` | How the system runs on a local Kubernetes cluster (spike 014) |
| `docs/test-bed/` | The `sample-crm` repository and task set used by runs (spike 002) |
| `deploy/` | Draft Kubernetes manifests: `base/` and the `local` and `gke` overlays (spike 014) |
| `scripts/` | Helper scripts, such as the test-bed baseline checks (spec 004) |
| `specs/` | The spec-driven kit: one folder per work item; `specs/README.md` explains it |
| `CLAUDE.md` | Working rules for Claude Code sessions in this repository |

## Stack

Java 25, Spring Boot 4.1, and Maven for every server-side service; PostgreSQL 18 with JPA, Hibernate, and Flyway; MCP Java SDK 2.1.0 in the gateway; fabric8 in the runner; Pub/Sub (the emulator locally); React, Vite, and TypeScript for the UI; JUnit 5 and Testcontainers for tests. Everything server-side runs on Kubernetes: Docker Desktop's cluster locally, GKE later.

## Build, test, and run

Not available yet. Phase 0 scaffolding adds the commands here and to `CLAUDE.md`. Until then, `docs/local-setup/README.md` describes the intended local loop.

## Working on it

All work goes through the spec kit in `specs/`:

```sh
specs/_kit/sdd status      # board and next up
specs/_kit/sdd next        # what can be worked on now
```

See `specs/README.md` for the commands and the rules every spec follows.
