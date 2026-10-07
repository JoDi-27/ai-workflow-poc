# Plan

Phased plan toward the MVP described in `WORKFLOW_ENGINE_INTENT.md`. Open questions and their suggested defaults live in `BACKLOG.md`; happy path step numbers below refer to the happy path defined there.

Each checklist item is delivered through a spec in `specs/` (see `specs/README.md`); `specs/STATUS.md` shows progress.

| Phase | Goal | Status |
| --- | --- | --- |
| 0. Spikes and scaffolding | Answer the unknowns, confirm the MVP decisions, and stand up empty but connected components | Not started |
| 1. Execution | Happy path steps 1–7: Claude Code builds and ships code through the engine, with Laya in shadow mode | Not started |
| 2. Observability and quality monitoring | Happy path steps 8–10 | Not started |
| 3. Feedback loop | Happy path steps 11–14 | Not started |
| 4. MVP validation | Run the whole happy path end to end on real tasks | Not started |
| 5+. Improvements | Work through `BACKLOG.md` "Next", in priority order | Not started |

## Phase 0: Spikes and scaffolding

Mostly learning and wiring. No workflow logic beyond stubs.

### Spikes

Each spike is a spike spec in `specs/`, whose findings and recommendation are its write-up, and ends with updates to the affected `BACKLOG.md` items. They are listed in the order to run them; the details, waves, and dependencies are in `BACKLOG.md`.

- [ ] **1. Concepts: what a workflow is**
- [ ] **2. Test bed: target repository and task set**
- [ ] **3. Definition model draft**
- [ ] **4. Claude Code driver feasibility**
- [ ] **5. Claude Code telemetry**
- [ ] **6. Laya/Jev role in the workflow**
- [ ] **7. Target architecture**
- [ ] **8. Driver API contract**
- [ ] **9. Technology stack**
- [ ] **10. Local setup**

### Decisions

- [ ] Confirm or replace each suggested default in the `BACKLOG.md` MVP section, using the spike results. Record each decision and its rationale in place.

### Scaffolding

- [ ] Repository layout, for example: `engine/` (Java), `ui/`, `driver-kits/claude-code/`, `laya-service/`, `definitions/`, `deploy/`, `docs/`.
- [ ] Engine skeleton: builds, starts, connects to the database, exposes a health endpoint and a stub MCP `status` operation.
- [ ] UI skeleton: separately deployed, logs in, and displays data from an engine endpoint.
- [ ] Claude Code driver kit skeleton: plugin with the engine MCP configuration and a session-start hook that calls the engine.
- [ ] Laya service stub: reachable from the engine through a stub DecisionEngine adapter.
- [ ] OTel Collector receiving Claude Code telemetry.
- [ ] Docker Compose bringing up engine, UI, database, collector, and Laya service.
- [ ] Build and test commands documented in the README.

### Exit criteria

- Every spike has a write-up, and every MVP item in `BACKLOG.md` has a decision.
- `docker compose up` starts all server-side components.
- A Claude Code session with the driver kit calls the stub `status` through MCP, and its session-start hook reaches the engine.
- Telemetry from that session arrives at the collector.
- The engine gets a response from the Laya service.

## Phase 1: Execution

Milestones 1–5 from the intent doc, happy path versions only.

- [ ] Definition model and the reference workflow in YAML, with validation.
- [ ] Engine core: runs, state and event tables, evidence, transition protocol, checkpoint execution, scoring with deductions.
- [ ] Driver API over MCP and HTTP with driver-token auth.
- [ ] Claude Code driver kit: instructions, session binding, activity hook, evaluator subagent, protected-action hook for the merge.
- [ ] UI: run list, run detail with score breakdown, human step inbox, UI login for human decisions.
- [ ] Laya: DecisionEngine interface, Laya adapter, the typed question chosen by the spike, in shadow mode.

**Exit criteria:** happy path steps 1–7 work on a real repository. A run ends with a merged branch and a score whose breakdown includes the Claude evaluator, Laya, and the human.

## Phase 2: Observability and quality monitoring

Milestones 6–7, happy path versions only.

- [ ] Telemetry ingestion from the collector into the engine, correlated by session ID.
- [ ] Per-run telemetry in the run detail page.
- [ ] Quality view: score trends per step and definition version, failure and rework rates, tokens and duration, evaluator agreement.

**Exit criteria:** happy path steps 8–10 work. A run's tokens and stage durations can be shown without asking Claude.

## Phase 3: Feedback loop

Milestone 8, happy path version only.

- [ ] Detection rule over the last N runs.
- [ ] Proposal flow in the driver kit: fetch analysis, draft a YAML diff with a hypothesis, submit it as a draft version.
- [ ] Approval page in the UI that publishes the new version.
- [ ] Quality view split by definition version.

**Exit criteria:** happy path steps 11–14 work. A published version has its rationale attached and can be compared with its predecessor.

## Phase 4: MVP validation

- [ ] Run the reference workflow on a handful of small, real tasks.
- [ ] Complete at least one full feedback cycle on the results.
- [ ] Write down what worked, what broke, and what to reprioritize in `BACKLOG.md`.

**Exit criteria:** all 14 happy path steps have been done for real, end to end. The MVP is ready.

## Phase 5+: Improvements

Pick from `BACKLOG.md` "Next", reprioritized with what Phase 4 taught us.
