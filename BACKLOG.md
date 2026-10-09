# Backlog

The PoC/MVP goal is one working happy path through all three pillars: execution, observability and quality monitoring, and the feedback loop. Laya is part of the MVP. When the happy path works end to end, the MVP is ready; everything after that is an improvement.

MVP items come with a suggested default to confirm or replace. Everything else stays on record but is not a priority.

## The happy path

### Pillar 1: Execution (build and ship code)

1. The engine runs with the reference workflow loaded.
2. A developer starts Claude Code with the driver kit and starts a run on a real repository.
3. Claude works through the stages, writes the code, submits evidence, and requests transitions.
4. The major checkpoint runs a deterministic step, a reasoning step scored by the evaluator subagent, and a human step approved in the UI.
5. Laya answers its typed question at that checkpoint, and the result is recorded next to the Claude evaluator's and the human's.
6. Shipping (for example, merging to the main branch) is a protected action that the engine authorizes through a hook.
7. The run completes, and the UI shows its score and breakdown.

### Pillar 2: Observability and quality monitoring

8. Claude Code's OpenTelemetry output reaches the collector, and the engine correlates it with the run by session ID.
9. The UI shows per-run telemetry (tokens, duration per stage, tool errors) next to the run's events and score.
10. A quality view shows trends across runs: run and step scores, failure and rework rates, and agreement between the Claude evaluator, Laya, and the human.

### Pillar 3: Feedback loop

11. From the results of several runs, the engine flags the weakest step or checkpoint.
12. In an interactive Claude Code session, Claude reads the analysis from the engine and proposes a definition change with a hypothesis.
13. A human reviews the diff and approves it in the UI, and the engine publishes it as a new definition version.
14. New runs use the new version, and the quality view compares it with the previous one.

## MVP: decide to build the happy path

### Execution

- [ ] **Engine framework:** Spring Boot or something lighter. *Suggested:* Spring Boot, for speed of setup.
- [ ] **Database:** *Suggested:* one relational database. Small evidence payloads and telemetry are stored in it directly, with no object storage yet.
- [ ] **State and events:** *Suggested:* state tables plus an append-only event table, written in the same transaction.
- [ ] **Definition format:** *Suggested:* YAML. The initial version is loaded from a file; later versions come from feedback-loop proposals approved in the UI. No general UI editor yet.
- [ ] **Deterministic steps:** *Suggested:* a few built-in check types, such as "evidence of type X exists" and "field compares to a value". No expression language.
- [ ] **MCP server:** *Suggested:* MCP Java SDK over HTTP with a bearer token.
- [ ] **Auth:** *Suggested:* one static driver token per user, and a simple UI login. Human decisions and definition approvals are accepted only from the UI login, even in the MVP.
- [ ] **UI technology:** *Suggested:* whatever is fastest to build. Pages refresh by polling.
- [ ] **Hooks to engine:** *Suggested:* hooks call the engine's HTTP API with a small script. No dedicated CLI yet.
- [ ] **Result delivery to the driver:** *Suggested:* polling `checkpoint_status`.
- [ ] **Runs and repositories:** *Suggested:* one active run per workflow and subject key; the SDLC driver kit uses repository + branch as the subject (spike 001, Q1). Shipping means merging that branch.
- [ ] **Evidence:** *Suggested:* the driver submits small inline payloads, such as test summaries and commit SHAs, marked driver-reported.
- [ ] **Activity recording:** *Suggested:* a post-tool-use hook sends tool name and outcome. No transcripts yet.
- [ ] **Evaluator subagent:** *Suggested:* read-only tools; evidence is included inline in the evaluation task.
- [ ] **Scoring:** *Suggested:* linear raw-to-points mapping; one per-workflow deduction for failed attempts and one for rework loops; councils limited to an odd number of members.
- [x] **Stage tasks:** *Decided (spike 001):* each non-terminal stage has a stage task (templated task, acceptance criteria, tests) with provider-neutral limits (iterations, wall time per visit, consecutive failed completion checks). The driver kit enforces the limits and the engine records each iteration. Exhaustion always escalates to the owner in the UI; other stops only pause. See `specs/001-concepts-what-a-workflow-is/`.
- [ ] **Reference workflow:** *Suggested:* `requirements → implementation → verification → release → complete`, with one major checkpoint before `release`, one rework edge, and the merge as the protected action.

### Laya

- [ ] **Runtime and serving:** Laya's requirements (language, hardware, memory, input limits) and how it is served next to the engine. *Suggested:* a separate local inference service with a minimal HTTP API, called by the engine's DecisionEngine adapter.
- [ ] **MVP use:** *Suggested:* one typed question in shadow mode at the major checkpoint, classifying the reasoning step into its rubric bands so it can be compared with the Claude evaluator and the human. Shown in the quality view, not on the approval screen.
- [ ] **Model checkpoint:** general English or typed-decisions English. *Suggested:* try both on a handful of local cases and keep the better one.
- [ ] **Hardware:** *Suggested:* the M5 Pro for the MVP; measure latency there.

### Observability and quality monitoring

- [ ] **Telemetry pipeline:** *Suggested:* an OpenTelemetry Collector that forwards to the engine, which stores only what it needs (tokens, cost, duration, tool results, errors) keyed by session ID. A Grafana-style stack is the alternative if raw exploration is needed.
- [ ] **Session correlation:** *Suggested:* the session-start hook registers the Claude Code session ID with the run; confirm that this ID is present in the telemetry.
- [ ] **Quality view:** *Suggested:* one UI page with score trends per step and per definition version, failure and rework rates, tokens and duration per run, and evaluator agreement.

### Feedback loop

- [ ] **Detection:** *Suggested:* a simple rule over the last N runs, such as the step with the lowest average normalized score or the highest failure rate.
- [ ] **Proposal flow:** *Suggested:* a skill or slash command in the driver kit that fetches the analysis from the engine, has Claude draft a YAML diff with a hypothesis, and submits it as a draft version.
- [ ] **Approval:** *Suggested:* a UI page showing the diff and hypothesis, with approve and reject; approval publishes the new version.
- [ ] **Comparison:** *Suggested:* the quality view splits metrics by definition version. With few runs, show raw numbers and run counts rather than claim significance.
- [ ] **Seed data:** *Suggested:* run the reference workflow on a handful of small tasks so the loop has results to work with.

### Local setup

- [ ] *Suggested:* Docker Compose running the engine, UI, database, OTel Collector, and Laya inference service.

## Spikes

Spikes are listed in the order to run them. Spikes in the same wave can run in parallel. Each spike names the spikes it depends on and the MVP items above that it settles.

| Wave | Spikes | Why at this point |
| --- | --- | --- |
| 1. Foundations | 1. Concepts, 2. Test bed | Shared vocabulary and real tasks that every later spike uses |
| 2. Unknowns | 3. Definition model, 4. Driver feasibility, 5. Telemetry, 6. Laya/Jev role | Settle the domain model and the external facts the architecture depends on |
| 3. Shape | 7. Target architecture, then 8. Driver API contract | Put the findings together into components, flows, and the provider-neutral boundary |
| 4. Tools | 9. Technology stack | Choose technology for an architecture that is already known |
| 5. Running it | 10. Local setup | Can only be designed once the stack is chosen |

### Wave 1: Foundations

- [x] **1. Concepts: what a workflow is.** *Done:* `specs/001-concepts-what-a-workflow-is/`, with the model in `docs/concepts/`. Turn the Concepts list in the intent doc into a precise domain model, so that every later spike uses the same terms. Questions to answer:
  - Workflow, workflow definition, definition version, and run: what each one is, and how they relate. What a run is attached to (task, branch, repository) and who owns it.
  - What a workflow's shape can be: a stage graph with rework edges. Can it have parallel stages, sub-workflows, or several entry and terminal stages? Decide what is outside the model for the MVP.
  - Stage, transition, checkpoint, evaluation step, executor, evidence, protected action, typed decision, driver session, lease, and evaluation task: a definition for each, and how they relate (an entity-relationship sketch).
  - Lifecycles as state machines: run (active, completed, failed, excluded), checkpoint attempt, step result (passed, failed, not evaluated, overridden), evaluation task, human step, and definition version (draft, published, superseded).
  - Which concepts the engine knows about and which exist only in definitions, so that SDLC terms stay out of the engine. Test this by sketching a non-SDLC toy workflow with the same concepts.

  Deliverable: a glossary, an entity-relationship sketch, and lifecycle diagrams, with the intent doc's Concepts section updated to match.
- [ ] **2. Test bed: target repository and task set.** *(Suggested addition.)* Choose where runs do real work and which tasks they do, so that the other spikes, the seed data, and the Phase 4 validation all use the same cases. Questions to answer:
  - Which sandbox repository to use, ideally one you own so that merging to it is harmless, and whether it needs CI for the later system-attested evidence.
  - Which 8–12 small tasks to use, with a mix of difficulty, including some that should fail a checkpoint or need rework, and the expected result for each.
  - Which of these become Laya's first labeled local cases.

  Deliverable: the repository and a task list with expected results in `docs/`. Depends on: nothing.

### Wave 2: Unknowns

- [ ] **3. Definition model draft.** Write the reference workflow in a draft YAML schema with stages, transitions, evidence, checkpoints, rubrics, scoring, deductions, Laya's typed question, and the protected action. Compute one example run's score by hand. Deliverable: the draft schema and example, ready to become milestone 1. Depends on: 1. Settles: Definition format, Deterministic steps, Scoring, Reference workflow. Input from spike 1: express its decisions in the schema, including stage tasks and their limits, and walk one run with a failed attempt, a rework loop, and an override.
- [ ] **4. Claude Code driver feasibility.** Confirm that the driver design works with Claude Code on a subscription:
  - Claude Code connects to a remote MCP server written with the MCP Java SDK.
  - A session-start hook can register the session ID with the engine.
  - A pre-tool-use hook can block a merge based on an engine response, and a post-tool-use hook can report activity.
  - An evaluator subagent with a fresh context and read-only tools can fetch a task and submit its result through MCP without the main agent relaying it.
  - All of it can be packaged as a plugin.
  - A full run, including evaluator subagents, fits within subscription usage limits.

  Deliverable: a write-up of what works, what doesn't, and workarounds, with a throwaway prototype. Depends on: nothing; run it alongside 5, which can reuse the same prototype. Settles: MCP server, Hooks to engine, Activity recording, Evaluator subagent. Input from spike 1: prototype a stage task driven by a Stop hook (does the 8-block cap fire despite progress; does Stop fire on Esc), an `http` Stop hook against a stub (block continues, failure lets Claude stop), and `/goal` with a turn limit in its condition. Known constraint: a plugin cannot raise the 8-block cap.
- [ ] **5. Claude Code telemetry.** Find out what Claude Code's OpenTelemetry export actually contains: metrics and events, session ID, tokens, cost, tool results, and how it is configured from a plugin or environment. Deliverable: a write-up listing what the quality view can and cannot show. Depends on: nothing; shares a prototype with 4. Settles: Telemetry pipeline, Session correlation.
- [ ] **6. Laya/Jev role in the workflow.** Work out where typed decision models actually add value, before the Laya MVP items are settled; their suggested defaults are placeholders until then. Questions to answer:
  - What Laya and Jev are in practice: inputs, outputs, confidence semantics, input limits, runtime and hardware needs, licensing, and availability (local or hosted).
  - Which roles are worth it: human step assist, typed step evaluation, transition guidance, and run categorization for the feedback loop.
  - Where in the reference workflow each role fits, and how it relates to the Claude evaluator and the human at the same point.
  - Which typed questions and label sets to start with, and how to label the first local cases.
  - Whether Laya and Jev overlap or complement each other.

  Deliverable: a short write-up recommending Laya's MVP use and the order of later roles, tried on a few local cases from the test bed. Depends on: 1, 2. Settles: all Laya items.

### Wave 3: Shape

- [ ] **7. Target architecture.** Combine the results of waves 1 and 2 into the system's shape, marking clearly what the MVP builds and what is only the target. Questions to answer:
  - Components and their boundaries: engine modules (definitions, runs and state, checkpoint execution, scoring, evaluation tasks, DecisionEngine adapters, telemetry ingestion, driver API, UI API), UI, Laya service, collector, database, and driver kit.
  - Sequences for each happy path step: start and bind, submit evidence, request a transition, run a checkpoint with deterministic, reasoning, human, and Laya steps, authorize a protected action, correlate telemetry, and propose and publish a definition version.
  - How long-running checkpoints progress: what moves a checkpoint forward while it waits hours for a human, and how the driver and UI learn about it.
  - Persistence: state tables, the event table, what can be replayed, and how requests on the same run are serialized and made idempotent.
  - Trust boundaries and auth: what the agent on the laptop can reach, how driver tokens and UI sessions are kept apart, and which risks the MVP accepts.
  - Where provider-neutrality and SDLC-neutrality are enforced in the module structure.

  Deliverable: an architecture document with component and sequence diagrams, plus a short decision record for each key choice. Depends on: 1, 3, 4, 5, 6. Settles: State and events, Auth, Result delivery to the driver, Runs and repositories, Evidence. Input from spike 1: how an open checkpoint attempt progresses while the driver waits on a human; how exhaustion escalations reach the owner, and what "owner directs a rework" does mechanically.
- [ ] **8. Driver API contract.** *(Suggested addition; can be folded into 7 if it stays small.)* Define the provider-neutral boundary that every driver kit is written against. Questions to answer:
  - The input and output schemas of each MCP operation, and which ones the MVP needs.
  - The HTTP equivalents that hooks call, and how they are authenticated.
  - The format of denial messages, idempotency keys, and the evaluation task packet (step, rendered rubric, evidence references).
  - Which driver capabilities a session declares, and how the engine checks them.

  Deliverable: the operation list with draft schemas, and one happy path run written as a sequence of calls. Depends on: 3, 4, 7. Input from spike 1: who computes the subject key, and how iterations and exhaustion escalations are reported.

### Wave 4: Tools

- [ ] **9. Technology stack.** Choose the technology for each component in the architecture. Questions to answer:
  - Engine: Java version, Spring Boot or a lighter framework, and how each fits the MCP Java SDK's server and transport support; build tool.
  - Database, migrations, and YAML parsing and validation for definitions.
  - UI: a single-page app or server-rendered pages, given that the UI is deployed separately and should be fast to build.
  - Laya serving: language and inference runtime, based on spike 6, and whether it can use the Apple GPU.
  - OTel Collector distribution, the hook script language (what is reliably installed on a developer laptop), and the test approach (for example, Testcontainers).

  Deliverable: a stack table with the choice, the reason, and the alternative rejected for each component, plus a hello-world through the chosen framework and MCP SDK. Depends on: 6, 7 (and 8 for the MCP and HTTP layer). Settles: Engine framework, Database, UI technology, Laya runtime and serving.

### Wave 5: Running it

- [ ] **10. Local setup.** Design how the whole system runs on one laptop, for development and for the MVP. Questions to answer:
  - What runs in Docker Compose and what runs natively. Containers on macOS generally cannot use the Apple GPU, so the Laya service may need to run on the host.
  - Networking: how Claude Code on the host reaches the engine's MCP endpoint and HTTP API, and how its telemetry exporter reaches the collector.
  - How to install the driver kit plugin from the local repository and configure its token.
  - Bootstrapping: creating a user and driver token, loading the reference workflow, seeding data, and resetting to a clean state.
  - The development loop: running the engine and UI from the IDE with hot reload while the other components run in Compose.

  Deliverable: a draft Compose file and a README section that takes a fresh clone to a stub `status` call from Claude Code. The Phase 0 scaffolding then implements it. Depends on: 9. Settles: Local setup.

## Next: improvements after the MVP

- [ ] **Laya beyond shadow mode:** advisory gate assist on the approval screen, transition recommendations, and promotion based on evidence.
- [ ] **Human step scoring:** the approver picks a rubric band in the UI.
- [ ] **Definition authoring** in the UI, beyond feedback-loop proposals.
- [ ] **System-attested evidence** from one CI or git hosting integration.
- [ ] **Real identity:** identity provider, roles, driver token expiry and revocation.
- [ ] **Multi-user robustness:** driver leases with heartbeat and takeover.
- [ ] **Evaluation tasks:** timeouts and retries when an evaluator never submits.
- [ ] **Transcripts:** upload, with secret redaction.
- [ ] **Dedicated CLI:** for humans and hooks. Startup latency matters, so possibly a native image or a non-JVM client.
- [ ] **Driver kit distribution and updates.**
- [ ] **Step-level deduction overrides.**
- [ ] **Richer feedback loop:** investigation across representative runs, predicted cost and regressions, revert flow.
- [ ] **Score validity:** outcome sources (defects, acceptance) and human spot-check sampling.
- [ ] **Non-SDLC toy workflow**, to catch SDLC assumptions in the engine. A paper sketch (article publication) is in `docs/concepts/model.md`.
- [ ] **Reuse of step results** across checkpoint attempts when their input revisions are unchanged (spike 001, Q7).
- [ ] **Maximum attempts per checkpoint**, ending the run when reached (spike 001, Q4).

## On record: not a priority

- [ ] Migrating in-flight runs to a newer definition version.
- [ ] Dry runs of draft definitions against recorded runs.
- [ ] Controlled experiments or staged rollouts between definition versions.
- [ ] Hash-chained, tamper-evident event log.
- [ ] Object storage for large evidence payloads.
- [ ] Expression language or plugins for custom check and evidence types.
- [ ] Custom raw-to-points mappings.
- [ ] Councils with an even number of members.
- [ ] Offline buffering of driver activity when the engine is unreachable.
- [ ] Retention policies for evidence, transcripts, activity, and telemetry.
- [ ] Multiple workspaces.
- [ ] API versioning and published API contracts.
- [ ] Database schema migration tooling.
- [ ] Laya bounded control.
- [ ] Jev and other DecisionEngine providers.
- [ ] A second driver provider after Claude Code.
- [ ] Parallel stages (fork and join) and sub-workflows in the definition model (spike 001, Q2).
