# Documentation

The design of the workflow engine, as settled by the Phase 0 spikes. Each document says which spike wrote it; that spike's `specs/NNN-*/spec.md` holds the questions, the user's answers, and the reasons. When a document and a spec disagree, the document is the current state and the spec is the history.

No product code exists yet, so everything here is design. The plan and the open decisions are in `PLAN.md` and `BACKLOG.md` at the repository root.

## Where to start

| If you want to | Read |
| --- | --- |
| Know what the product is and why | `intent.md`, Goal and Design guardrails |
| Learn the terms (run, subject, checkpoint, evidence, runner, DecisionEngine) | `concepts/glossary.md` |
| See the system's components and how they talk | `architecture/README.md`, Components |
| Follow one run from start to merge | `architecture/sequences.md`, then `driver-api/happy-path.md` |
| Write or change a workflow definition | `definitions/schema.md` and `definitions/sdlc-reference.yaml` |
| Build part of the engine | `engine/design.md`, the section for that part, and `engine/schema-draft.sql` |
| Build a driver kit or the MCP gateway | `driver-api/README.md` |
| Build the runner or its sandboxes | `architecture/README.md`, Runner interface, then `local-setup/README.md` |
| Run the system on a laptop | `local-setup/README.md` and the manifests in `deploy/` |
| Pick or check a test task | `test-bed/README.md` |
| Know why a design choice was made | `architecture/decisions.md` (AD-1 to AD-29), then the spike's spec |

## Contents

| Path | What it holds | Spike |
| --- | --- | --- |
| `intent.md` | Product intent: goal, runtimes, concepts, responsibilities, evidence, scoring, driver integration, typed decisions, and design guardrails. Long; read the section you need | Source of all spikes |
| `concepts/glossary.md` | Every term, grouped by definition time, run time, or both | 001 |
| `concepts/model.md` | Entities and relationships, and the lifecycle of each one as a state machine | 001 |
| `definitions/schema.md` | The YAML definition schema (draft v0), its semantic rules, and the scoring rules | 003 |
| `definitions/sdlc-reference.yaml` | The reference SDLC workflow written in that schema | 003 |
| `definitions/example-run.md` | One run of the reference workflow, scored by hand | 003 |
| `architecture/README.md` | Components, engine modules (logical view), configuration, runner interface, persistence, and trust boundaries | 010 |
| `architecture/sequences.md` | The happy path as sequence diagrams | 010 |
| `architecture/decisions.md` | One short record per key architecture choice | 010 |
| `driver-api/README.md` | The driver contract: HTTP operations, the MCP tools that mirror them, and draft schemas | 011 |
| `driver-api/happy-path.md` | Test-bed task T01 as a list of driver calls | 011 |
| `engine/design.md` | Engine low-level design: Maven modules, ports, domain model, schema, state machines, transactions, validator, errors, and testing | 013 |
| `engine/schema-draft.sql` | The draft engine schema DDL | 013 |
| `local-setup/README.md` | How the whole system runs on one laptop's Kubernetes cluster | 014 |
| `test-bed/README.md` | The `sample-crm` repository, its baseline, and tasks T01–T12 with their expected results | 002, 004 |

The technology stack is in the repository `README.md`, and the full stack decision is in spike 012's spec.

## Elsewhere in the repository

| Path | What it holds |
| --- | --- |
| `README.md` | What the project is, its status, and the stack |
| `CLAUDE.md` | Working rules for Claude Code sessions |
| `PLAN.md` | Phases, their checklists, and exit criteria |
| `BACKLOG.md` | The happy path, MVP decisions, a summary of each spike, carried-over checks, and later work |
| `specs/` | One folder per spec; `specs/README.md` explains the kit and `specs/STATUS.md` is the board |
| `specs/constitution.md` | The rules every spec, plan, and piece of code must respect |
| `deploy/` | Draft Kubernetes manifests: `base/` and the `local` and `gke` overlays (spike 014) |
| `scripts/` | Helper scripts, such as `baseline-checks.sh` for the test bed (spec 004) |

## Rules for these documents

- Plain, short sentences, consistent with the existing files.
- Each document starts by naming the spike or spec it comes from.
- A document changes through a spec. Small fixes that change no design are `[meta]` commits.
- Use the terms in `concepts/glossary.md`. Name spikes by their spec ID (spike 010, not "spike 7").
- Add each new document to the Contents table above.
