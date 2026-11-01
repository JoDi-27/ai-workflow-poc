# ai-workflow-poc

A proof of concept of a governed workflow engine for coding-agent drivers. The engine is the single authority for workflow definitions, runs, state, checkpoints, and scoring. A separate UI handles human decisions and monitoring. Drivers are interactive coding agents on developer laptops (Claude Code first) that reach the engine through MCP and HTTP, and a runner next to the engine validates gates in throwaway sandboxes. The reference SDLC workflow only exists to exercise the framework; the framework is the product.

## Status

Phase 0: the spikes are done and scaffolding is next. There is no product code yet. `specs/STATUS.md` is the progress board.

## Read first

| Path | What it holds |
| --- | --- |
| `docs/` | The design: intent, concepts, architecture, driver API, engine, definitions, local setup, and test bed. Start at `docs/README.md` |
| `PLAN.md` | Phases, checklists, and exit criteria |
| `BACKLOG.md` | The happy path, MVP decisions, a summary of each spike, carried-over checks, and later work |
| `specs/` | The spec-driven kit: one folder per work item. Start at `specs/README.md` |
| `engine/` | The engine. Empty until the engine skeleton; see its README |
| `mcp-gateway/` | The MCP gateway in front of the engine's driver API. Empty for now; see its README |
| `analysis/` | The analysis service behind the quality view. Empty for now; see its README |
| `ui/` | The UI. Empty until the UI skeleton; see its README |
| `runner/` | The runner and its sandbox Jobs. Empty until the runner stub; see its README |
| `driver-kits/` | Driver kits and their local plugin marketplace: `claude-code/`, with the OTel Collector configuration in `claude-code/collector/`. Empty for now; see its README |
| `definitions/` | Workflow definitions and their JSON Schema. Empty until Phase 1; see its README |
| `deploy/` | Draft Kubernetes manifests: `base/` and the `local` and `gke` overlays |
| `scripts/` | Helper scripts |
| `.claude/` | Claude Code skills, agents, hooks, and permissions. Start at `.claude/README.md` |
| `CONTRIBUTING.md` | Setup, the spec-first rule, and the commit message format |
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

See `specs/README.md` for the commands and the rules every spec follows, and `CONTRIBUTING.md` for the one-time setup and the commit message format.
