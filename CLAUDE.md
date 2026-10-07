# ai-workflow-poc

A PoC/MVP of a governed workflow engine for coding-agent drivers. The engine (Java) is the single authority for workflow definitions, runs, state, checkpoints, and scoring. A separately deployed UI handles human decisions and monitoring. Drivers are interactive coding agents on developer laptops (Claude Code first) that reach the engine through MCP and HTTP. Laya, a typed decision model, runs next to the engine. The SDLC reference workflow only exists to exercise the framework; the framework is the product.

## Current state

Phase 0 (spikes and scaffolding), not started. There is no code yet: no build, no tests, no runnable components. The tech stack is open until spike 9 settles it; do not assume Spring Boot, a database, or a UI framework just because `BACKLOG.md` suggests one. When scaffolding lands, add the build, test, and run commands to this file.

## Where things live

| File | Read it for |
| --- | --- |
| `WORKFLOW_ENGINE_INTENT.md` | Product intent, concepts, and design guardrails (long; read the relevant section, not all of it) |
| `PLAN.md` | Phases, their checklists, and exit criteria |
| `BACKLOG.md` | The 14-step happy path, MVP decisions with suggested defaults, spike details and dependencies, and post-MVP items |
| `specs/constitution.md` | The rules every spec and plan must respect |
| `specs/README.md` | How the spec kit works |
| `specs/NNN-slug/` | One work item: `spec.md`, `plan.md`, `tasks.md` |
| `specs/STATUS.md` | Generated progress board; never edit by hand |
| `.claude/skills/sdd-*` | The six spec-kit skills |
| `.claude/agents/spike-researcher.md` | Prepares spike briefings and questions for the user |

The planned repository layout (not created yet) is `engine/`, `ui/`, `driver-kits/claude-code/`, `laya-service/`, `definitions/`, `deploy/`, `docs/`.

## How work is done

All work goes through the spec-driven kit in `specs/`.

- Do not implement anything that has no spec in `specs/`. Create one with `/sdd-specify` first, even for small items; a spike or a short feature spec is fine.
- Features follow `/sdd-specify` → `/sdd-plan` → `/sdd-implement` → `/sdd-verify`. Stop at the end of each step for the user's review; running the next command is the approval.
- Spikes follow `/sdd-spike` → `/sdd-verify`: an interview with the user, prepared by the `spike-researcher` agent. Whenever the user shares information about an active spike, even outside the command, record it in that spike's Log and Findings.
- Parallel sessions are fine: claim a spec or task with `specs/_kit/sdd claim` before working on it, and never pick work by reading the files. The skills do this.
- Keep the spec's status, its task checkboxes, and `specs/STATUS.md` up to date whenever work moves (`specs/_kit/sdd status --write`).
- When code and spec disagree, update the spec or plan first, then the code (Article 12).
- `/sdd-status` or `specs/_kit/sdd next` shows what to work on next.
- When a spec settles a `BACKLOG.md` item or finishes a `PLAN.md` checklist item, update those files in place.

Shell helpers:

```sh
specs/_kit/sdd new feature "Title"    # or: new spike "Title"
specs/_kit/sdd status                 # board and next up
specs/_kit/sdd status --write         # also regenerates specs/STATUS.md
specs/_kit/sdd next                   # only what can be worked on now
specs/_kit/sdd claim <ID> [next|Tn]   # claim a spec, or its next free task, before working on it
specs/_kit/sdd release <ID> [Tn] [--done]
specs/_kit/sdd claims [--prune]       # what other sessions hold
```

## Rules to keep in mind

The full list is in `specs/constitution.md`. The ones most easily broken while coding:

- **The engine is the sole authority.** Only the engine writes state, applies pass conditions, and computes scores. The UI and drivers are API clients and never touch storage.
- **Humans decide human steps.** Human decisions and definition approvals come only from human-authenticated UI sessions. Driver tokens never carry approval scopes. Model output (driver agent or Laya) can inform a human step but never satisfy it.
- **The engine is neutral.** No SDLC concepts and no Claude Code concepts in engine code; those belong in workflow definitions and driver kits.
- **Subscription-compatible drivers.** The engine never invokes a driver's model, and nothing relies on API-key orchestration.
- **Fail closed.** If the engine, an evaluator, or a decision provider is unavailable, protected actions and required steps do not pass.
- **Happy path first.** Build the simplest thing that advances a happy path step. Anything else goes to `BACKLOG.md` "Next", not into the current spec.
- **Verify current docs.** Check the current documentation of any library, SDK, or tool before using it (Context7), and record the version in the plan.

## Conventions

- Specs: `spec.md` holds what and why with no technology; `plan.md` holds how with no new requirements. Requirements use EARS, every requirement maps to an acceptance criterion, and every criterion to a test or recorded manual check.
- Unknowns are written as `[NEEDS CLARIFICATION: …]`, never guessed; they must be resolved before `/sdd-plan`.
- Real choices between alternatives get a row in the plan's Decisions table (or a spike's Recommendation).
- Tasks are half a day at most and leave the build green. One task is about one commit.
- Commit only when asked. Messages start with the spec and task ID, for example `[003] T2 Add run state table`.
- Docs are written in plain, short sentences, consistent with the existing Markdown files.

## Glossary

- **Run:** one execution of a pinned workflow definition version, in the MVP one per branch.
- **Checkpoint:** an ordered sequence of steps (deterministic, reasoning, or human) that gates a transition and earns points.
- **Evidence:** data a driver submits for a step, with its provenance (in the MVP, *driver-reported*).
- **Protected action:** an action, such as the merge, that a driver hook must get the engine to authorize.
- **Driver kit:** the per-provider package (MCP config, hooks, instructions, evaluator subagent) that turns an agent into a driver.
- **DecisionEngine:** the engine-side interface for typed decision models. Laya is the first provider (shadow mode in the MVP); Jev may come later.
