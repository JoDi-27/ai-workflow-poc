# Intent: governed workflow engine for coding-agent drivers

Status: design intent  
Starting point: greenfield  
Product: a workflow framework (a Java engine service and a separately deployed UI), not a specific SDLC  
Drivers: interactive coding agents on developer laptops, provider-neutral by contract  
Initial driver: Claude Code, using a Claude subscription  
Reference workflow: a small SDLC used to exercise and test the framework

## Goal

Build a standalone, deployable workflow engine, with a separate UI, in which workflows are defined, versioned, run, monitored, and scored. The engine is the single authority for stages, transitions, prerequisites, and human approvals, and it holds shared state for every user. The work inside each stage is performed by a driver: an interactive coding agent on a developer's laptop that reaches the engine through MCP or a CLI. Claude Code is the first driver and the initial focus. The engine and its driver contract are provider-neutral so that other agent providers can be added later without engine changes.

Every run ends with a quantified, decomposable score, for example 27 out of a possible 100. The score is earned at checkpoints: ordered sequences of evaluation steps, each of which can be an exclusion criterion, a source of points, or both. Steps that involve reasoning are performed by a fresh-context evaluator and scored against rubric guidelines written into the step definition.

Typed decision models, starting with Laya, are a first-class part of the framework. They assist human gates, act as evaluators for typed steps, and recommend transitions, under explicit trust levels that are raised only on measured evidence.

The framework rests on three pillars, and the MVP needs a working happy path through all three:

1. **Execution:** the engine, UI, driver, checkpoints, scoring, and Laya, which together let an agent actually build and ship code under governance.
2. **Observability and quality monitoring:** driver telemetry (starting with Claude Code's OpenTelemetry output) correlated with runs, and monitoring of run quality and of the scoring itself.
3. **Feedback loop:** improvements to workflow definitions, proposed from run results, approved by a human, and measured against the previous version.

The SDLC is not the product. A reference SDLC workflow exists to test the framework end to end and to prove that the definition model is expressive enough.

## Runtimes and deployables

There are two runtime sides: a shared server side and a driver side on each developer laptop. The server side consists of separately deployed components.

| Component | Runs on | Technology direction | Role |
| --- | --- | --- | --- |
| Engine | Shared server side | Java; whether to use Spring is to be decided | Sole authority for definitions, runs, state, event log, checkpoint execution, scoring, and typed decisions. Owns all persistence. Integrates with Laya and Jev. Exposes the driver API (MCP and HTTP) and the UI API. |
| UI | Shared server side, deployed separately from the engine | To be decided | Definition authoring and publishing, run monitoring, score breakdowns, human step inbox. Talks to the engine only through its API and holds no authoritative state. |
| Decision model serving | Next to the engine, called only by the engine | Depends on the provider | Serves Laya inference, and later Jev or other providers. |
| Driver | Developer laptop | Depends on the agent provider | An agent session (initially Claude Code) plus that provider's driver kit: engine MCP configuration, hooks, instructions, evaluator, CLI. |

- **Authority:** only the engine commits state, computes scores, and writes to persistence. The UI and drivers are both clients of the engine API; neither accesses storage directly.
- **Model use:** the engine uses only DecisionEngine providers (Laya first) and deterministic code. The driver uses its own model through the user's subscription or account, for both work and fresh-context evaluation.
- **Users:** the engine serves many authenticated humans, through the UI, and many drivers. Each driver session has one human and one agent.

The engine never invokes a driver's model. The driver changes engine state only through requests the engine validates. If the engine is unreachable or the driver's view is stale, the driver stops protected actions and reports the recovery step; it never proceeds on cached state.

## Operating constraints

- Drivers are interactive and subscription-compatible: a human starts the agent, and the agent calls engine tools while working. The design must not depend on API-key orchestration, unattended model invocation, or a usage-based API budget. For the initial Claude Code driver, this means working on a Claude subscription. Engine-side intelligence comes only from DecisionEngine providers and deterministic code.
- Only the engine commits state transitions, applies pass conditions, and computes scores. The driver may request a transition and submit evaluation results; the engine may recommend a transition.
- Human steps are decided in the UI by an authenticated human, and the engine accepts human decisions only from human-authenticated UI sessions. Driver credentials (MCP and CLI tokens) never carry approval rights, so the agent, which can read files and run commands on the laptop, cannot approve a gate even with the developer's driver token.
- A model output, whether from a driver agent or Laya, may inform a human step but can never satisfy one or silently remove one.
- Deterministic steps are checked by code against evidence with recorded provenance, not inferred from prose.
- Reasoning steps are performed in a fresh context window by an evaluator that did not do the work, against a rubric defined in the step, with the evaluator, evidence, and result recorded.
- Every run is pinned to a workflow definition version, including its checkpoints, rubrics, and scoring formula. Definition changes affect new runs unless a deliberate, audited migration is approved.
- The engine contains no SDLC-specific concepts. Stages, evidence types, checkpoints, and rubrics come from workflow definitions.
- The engine contains no driver-provider-specific concepts. Provider specifics live in driver kits behind the driver contract.
- Enforcement on the developer's machine is trust-based. What cannot be enforced there is still recorded.
- English is the working language.

## Concepts

The full domain model (glossary, entity relationships, and lifecycles) is in `docs/concepts/`, from spike 001.

- **Workflow:** the stable identity that groups the versions of one definition. It has exactly one current published version, and new runs always start on it.
- **Workflow definition (version):** a versioned, reviewable specification of stages and their stage tasks, transitions (including rework edges), evidence types, checkpoints, typed decisions, protected actions, and the scoring formula. Rubrics, scoring, and decision questions are versioned only as part of it. It is authored and published through the UI and can be exported and imported as text for review and diffing.
- **Stage:** a node of the workflow graph. A definition has one entry stage and one or more terminal stages, each marked success or failure; a run is in exactly one stage at a time. Parallel stages and sub-workflows are outside the MVP model.
- **Stage task:** the self-contained, validated piece of work a non-terminal stage asks for: a task, acceptance criteria, tests, and provider-neutral limits, configured per workflow from framework templates. The driver does the work, typically by iterating, and enforces the limits; the engine records it. It does not replace checkpoints; its test results become evidence.
- **Transition:** a permitted move between stages, with zero or one checkpoint. A *rework edge* is a transition explicitly flagged `rework`.
- **Run:** one execution of a pinned definition version on a *subject*, which the engine knows only as an opaque subject key (at most one active run per workflow and subject key). It has a fixed owner, a status (active, completed, failed, abandoned), a current stage, stage visits, evidence, step results, pending approvals, decision records, an event sequence, and a final score.
- **Evidence:** a typed, referenced item submitted during a stage visit, with provenance, an opaque revision, timestamp, and trust level.
- **Checkpoint:** an ordered sequence of evaluation steps attached to a transition. Major checkpoints sit at consequential boundaries; stage checks are optional checkpoints on any other transition. Both follow the same rules.
- **Checkpoint attempt:** one evaluation of a checkpoint for a transition request. Steps run strictly in order and fail fast; every attempt re-runs all steps. A run has at most one open attempt.
- **Evaluation step:** one unit of a checkpoint with an executor, a required or optional role, a maximum number of points, a pass condition, and, for reasoning steps, a scale and rubric.
- **Executor:** what performs a step: deterministic code, a fresh-context agent evaluator, a human in the UI, or a DecisionEngine provider.
- **Run score:** the engine's computation of points earned over points possible across the run's applicable steps, normalized to 100. Its classification (scored, overridden, or excluded) is derived from the run status.
- **Typed decision:** a question answered by a DecisionEngine with a bounded set of labels, in a declared mode (shadow, advisory, or bounded control).
- **Protected action:** a driver-side action, such as merge, release, or deploy, that requires engine authorization at the moment it is taken, and is allowed only in the stages the definition lists.
- **Driver contract:** the provider-neutral set of capabilities a coding agent must provide to drive runs, delivered per provider as a driver kit.
- **Driver session:** a binding between an agent session and a run, used for leases, attribution, activity recording, and telemetry correlation.

## Responsibilities

| Component | Owns | Does not own |
| --- | --- | --- |
| Engine core | Definitions and versions, runs, permitted transitions, checkpoint execution, pass conditions, scoring, event log, replay, idempotency, leases | Coding or open-ended reasoning |
| UI (separate deployable) | Definition authoring and publishing, run monitoring, score breakdowns, human step inbox, overrides, review and promotion of typed decisions | Authoritative state, direct storage access, or bypassing the engine's evaluator |
| Engine API | Driver API (MCP and HTTP, also used by the CLI) and UI API, with stable operation schemas, scoped authentication, and actionable denial messages | Accepting human decisions from driver credentials |
| Engine persistence | Configuration, run state and event log, evidence metadata and referenced artifacts | Access by anything other than the engine |
| Driver (coding agent, initially Claude Code) | Planning, implementation, debugging, review, investigation, evidence submission, transition requests, launching evaluators, reporting its activity | Self-approval, self-evaluation, or direct state mutation |
| Fresh-context evaluator (driver side) | Rubric-based assessment of a single step from an engine-issued task, with justification and uncertainty | Seeing the working conversation, applying thresholds, or computing points |
| Driver hooks | Session binding, fail-closed interception of protected actions at tool boundaries | Being the authoritative check |
| Human | Human steps, overrides with reasons, definition publishing, risk acceptance, promotion of typed decisions | Reconstructing hidden runtime state |
| Deterministic steps | Test results, artifact existence, required fields, tool outcomes, measurable invariants | Semantic adequacy inferred from prose |
| DecisionEngine (Laya, later possibly Jev) | Typed estimates, human step assistance, typed step evaluation, transition recommendations | Workflow authority, human approval, deterministic steps, causal explanations |

## Multi-user and shared state

- Humans sign in to the UI through an identity provider. Drivers use per-user scoped tokens. Scopes separate driver operations (read status, submit evidence, request transitions, submit step results, authorize protected actions) from human operations (decide human steps, override, publish definitions, promote typed decisions).
- Every event records the actor's identity and type: human, driver, evaluator, engine, or decision engine.
- A run has an owner and at most one active driver lease. Lease takeover is explicit and logged. Concurrent requests on a run are serialized, and idempotency keys prevent double advancing.
- A human step may require separation of duties, for example that the approver is not the run's driver.
- Start with one organization and workspace, but keep a workspace scope in the data model.

## Persistence

The engine owns all persistence. It holds three kinds of data with different lifecycles:

- **Configuration:** workflow definitions and their versions, checkpoints, rubrics, scoring formulas and deductions, typed decision questions and modes, driver capability requirements, DecisionEngine provider settings, workspaces, roles, and users. Drafts are editable; published versions are immutable and referenced by the runs pinned to them.
- **Run state:** runs, current stages, leases, checkpoint attempts, step results, pending human steps, decision records, scores, and the append-only event log from which state and scores can be replayed.
- **Evidence:** evidence metadata (type, provenance, trust level, revision, timestamp) kept with run state, plus large payloads such as artifacts, transcripts, and driver activity kept in referenced, content-addressed storage.

Configuration and run state need transactional, atomic updates, for example from a relational database. Large payloads go to object or file storage and are referenced by hash. The concrete stores are an open decision.

## Evidence and trust

The engine does not run on the laptop, so it cannot directly observe most local work. Each evidence item carries a trust level, and each deterministic step declares the minimum level it accepts:

- **Engine-verified:** the engine checked it itself, for example by resolving a commit on the shared remote, hashing an uploaded artifact, or validating required fields.
- **System-attested:** reported by an independent system integrated with the engine, such as a CI result, pull request status, or code review approval.
- **Driver-reported:** submitted by the driver, such as local test output or a driver-side evaluator result, recorded with session, command, revision, and timestamp.

Evidence is validated at decision time against its revision identifier. Changed artifacts or expired results require reevaluation. Large artifacts and traces are kept in referenced storage, not inside events.

## Checkpoints, steps, and scoring

### Step definition

Each step in a checkpoint declares:

- **Executor:** deterministic, fresh-context agent evaluator, human, or DecisionEngine provider (subject to its mode).
- **Role:** *required* steps are exclusion criteria: if a required step fails, the checkpoint fails. *Optional* steps are nice-to-haves: they never block and only earn points.
- **Points:** the maximum the step contributes to the run score. A pure exclusion step has zero points; a required step can also earn points.
- **Pass condition:** for deterministic steps, a predicate over evidence; for scored steps, a minimum raw score.
- **Scale and rubric** for reasoning and human steps: a numeric range divided into bands, each with a descriptor and optionally examples.
- **Inputs:** the evidence types the executor receives and the minimum trust level accepted.
- **Council** (optional): the number of independent evaluators for a reasoning step. The default is 1; a step that needs more declares a council of N explicitly.
- **Deductions** (optional): points subtracted for each failed attempt of the step, overriding the definition's default deduction.

Illustrative step:

```yaml
id: code_quality
executor: agent_evaluator
role: required
points: 20
scale: [1, 20]
pass: { min_score: 6 }
inputs: [diff, design_doc]
rubric:
  - range: [1, 5]
    descriptor: No meaningful quality. Unreadable, unstructured, or ignores the design.
  - range: [6, 10]
    descriptor: Acceptable. Works and follows the design, with clear maintainability issues.
  - range: [11, 15]
    descriptor: Good. Clear structure and naming; minor issues only.
  - range: [16, 20]
    descriptor: Exemplary. Idiomatic, well-factored, and easy to extend.
```

### Execution rules

- Steps run in their declared order and fail fast: a failed required step stops the checkpoint immediately, and every remaining step, required or optional, is recorded as *not evaluated* for that attempt.
- Executors return a raw result only: a raw score, the chosen band, a justification citing evidence, and uncertainty. The engine applies pass conditions, maps raw scores to points, and computes totals.
- Unless a definition specifies otherwise, a scored step earns `points × (raw − scale_min) / (scale_max − scale_min)`, and a pass/fail step earns full points on pass and zero on fail.
- A failed checkpoint rejects the transition, and the run stays in its current stage. What follows is another attempt, a rework edge, or a transition to a failure terminal stage, which ends the run as failed.
- A human may override a failed required step in the UI with a reason. The override is recorded, and the run's score is flagged as overridden.

### Run score

- At completion, the engine computes earned points over possible points using the latest evaluation of each step on the path the run took, normalized to 100. Raw earned and possible points are stored as well.
- Steps on branches the run did not take are not applicable and do not count toward possible points. Applicable steps that were never evaluated earn zero and are flagged *not evaluated*, distinct from *failed*.
- A run that ends on a failed required step is reported as *excluded*, with its partial score shown separately, not as an ordinary low score.
- Failed attempts and rework loops cost points. Each workflow definition configures its own default deduction per failed attempt of a required step and per rework loop, and individual steps may override it. Deductions appear as separate line items in the breakdown, and the final score never goes below 0.
- The breakdown per checkpoint and step (raw score, band, points, executor, evidence, attempt, deductions) is stored and shown in the UI.
- The scoring formula is versioned with the definition. Scores are compared only between runs with the same scoring version unless a documented mapping exists.

### Fresh-context evaluation

- A step that involves reasoning is executed in a fresh context window by an evaluator that did not perform the work and never sees the working session's conversation.
- Because the engine never invokes a driver's model, the driver runs these evaluators. The engine issues an evaluation task containing a task ID, the step definition, the rubric rendered by the engine, and evidence references. The driver kit's evaluator (for Claude Code, a subagent with its own context window and read-only tools) fetches the task, evaluates, and submits its result directly to the engine with the task ID. The working agent only launches the evaluator; it neither writes the prompt nor relays the result.
- For a council of N, the engine issues N tasks for the step, and each member runs as a separate evaluator that never sees the others' results. The engine aggregates the raw scores by median before applying the pass condition, and records every member's result and the spread.
- The engine records the evaluator's provider, agent definition and version, model, task ID, input packet hash, result, and a transcript reference.
- Isolation on the developer's machine is trust-based; the engine does not try to enforce it. Instead, everything the driver does is recorded, including evaluation tasks, submissions, tool activity, and transcript references, so any evaluation can be audited afterwards. Driver-side results carry the *driver-reported* trust level. For stronger independence, use engine-side Laya for typed steps, human steps for high-stakes judgments, and human spot checks.

## Transition protocol

1. The driver asks for status and receives the current stage, objective, the checkpoint guarding each permitted transition, pending evaluation tasks, pending human steps, and any active transition recommendation.
2. Work produces artifacts and results. The driver or an integrated system submits evidence; the engine records it with provenance and trust level.
3. The driver requests a specific permitted transition with evidence references and an idempotency key. This is a proposal, never a state change.
4. The engine runs the transition's checkpoint against the run's pinned definition version, one step at a time in declared order: it evaluates a deterministic step itself, issues evaluation tasks when it reaches a reasoning step, queues a human step in the UI when it reaches one, and requests typed decisions according to their modes. A step starts only after the previous one has a result.
5. Once every required step has a result, the engine accepts and commits the transition atomically, or rejects it, listing the failed steps with reasons and the permitted rework path. The driver tracks progress through `checkpoint_status`.

At minimum, record `RUN_STARTED`, `DRIVER_SESSION_BOUND`, `LEASE_ACQUIRED`, `LEASE_RELEASED`, `STAGE_ENTERED`, `EVIDENCE_SUBMITTED`, `DRIVER_ACTIVITY_RECORDED`, `TRANSITION_REQUESTED`, `CHECKPOINT_STARTED`, `EVALUATION_TASK_ISSUED`, `STEP_EVALUATED`, `DECISION_REQUESTED`, `DECISION_RECORDED`, `HUMAN_STEP_REQUESTED`, `HUMAN_STEP_DECIDED`, `CHECKPOINT_COMPLETED`, `TRANSITION_REJECTED`, `TRANSITION_ACCEPTED`, `STAGE_EXITED`, `PROTECTED_ACTION_AUTHORIZED`, `PROTECTED_ACTION_DENIED`, `POLICY_OVERRIDE`, `RUN_COMPLETED`, `RUN_SCORED`, and `DEFINITION_PUBLISHED`. Each event includes actor, actor type, timestamp, run ID, definition version, correlation ID, driver session ID where applicable, evidence references, and reason. Events are append-only in normal operation, tamper-evident (for example, hash-chained), and sufficient to replay a run's state and recompute its score.

Human step decisions record approver identity, scope, decision, raw score where the step is scored, time, and reason. Overrides require an explicit reason and remain visible in monitoring, scoring, and evaluation.

## Driver integration

The engine exposes a remote MCP server and an equivalent CLI over the same API. Initial operations: `start_run`, `bind_session`, `status`, `allowed_actions`, `submit_evidence`, `request_transition`, `checkpoint_status`, `get_evaluation_task`, `submit_step_result`, `record_activity`, and `authorize_action`. Each has a stable schema and useful denial messages.

### Driver contract

Claude Code is the first driver and the initial focus, but the engine is provider-neutral: any interactive coding agent can become a driver by shipping a driver kit that meets this contract. Each session records its driver provider, agent version, model, kit version, and declared capabilities.

- **Core (required):** call the engine through MCP or the CLI; bind a session to a run; consult status when starting or resuming work and at proposed stage boundaries; submit evidence and transition requests.
- **Activity recording (required):** report the agent's tool activity to the engine and reference session transcripts, so that everything the driver does is recorded even where it cannot be enforced.
- **Evaluation:** run an engine-issued evaluation task in a fresh context and submit its result directly. Without this capability, reasoning steps must be executed by Laya or a human.
- **Enforcement:** intercept protected actions before they run, call `authorize_action`, and fail closed.
- **Telemetry:** export telemetry that the engine can correlate with the session.

A workflow definition can require capabilities, for example an enforcement-capable driver for runs with protected actions. The engine refuses to bind a session whose driver lacks a required capability.

### Claude Code driver kit

The first kit ships as a versioned, one-step installable Claude Code plugin that bundles the MCP configuration, hooks, instructions, evaluator subagent, and CLI.

- Instructions make Claude consult the engine when starting or resuming work and at proposed stage boundaries, and launch the evaluator subagent for each pending evaluation task.
- A session-start hook binds the Claude Code session to a run and loads current status.
- Post-tool-use hooks report tool activity to the engine, and the session transcript is referenced when the session ends.
- Pre-tool-use hooks intercept protected actions and call `authorize_action`. A missing engine, stale state, or failed checkpoint stops the action and shows the recovery step.
- Hooks enforce only at the tool boundaries they intercept and cannot catch every side effect. Authoritative protection belongs on the engine side: where possible, perform protected actions through paths that check the engine, such as a release pipeline that holds the credentials and verifies the run state.

## Typed decisions and Laya

Laya is designed in from the start, but its influence is earned for each use.

**Contract.** A provider-neutral `DecisionEngine` interface receives a bounded state packet (stage, evidence summaries, step results, relevant history), a named typed question, a question or rubric version, and the permitted labels. It returns a normalized label, the full distribution where available, provider, model, and version, latency, and raw provider metadata. Adapters are written and tested independently; compatible wire formats do not imply identical confidence definitions or behavior.

**Roles.**

1. **Human step assist:** show a typed assessment, such as readiness, unresolved risk, or need for deeper review, next to the evidence on the UI approval screen. Record whether the approver agreed, and watch for automation bias. It never pre-approves.
2. **Typed step evaluation:** act as an engine-side executor for steps that can be posed as typed questions. Rubric bands map naturally to labels, and the distribution over bands expresses uncertainty. This gives an evaluator that is independent of the driver's model and of the developer's laptop.
3. **Transition guidance:** where several transitions are permitted, such as proceed, rework, or escalate, recommend one from the permitted set. The recommendation is surfaced to the driver through `status` and `allowed_actions` and to humans in the UI.
4. **Later:** categorize underperforming runs for the feedback loop.

**Modes**, declared for each use in the workflow definition and changed only by publishing a new version:

- **Shadow:** computed and logged with the state snapshot or hash, the driver's proposal, the engine decision, the human decision, and the later outcome. Influences nothing. For steps, Laya runs alongside the step's primary executor so the two can be compared.
- **Advisory:** shown to the driver and/or humans. Has no effect on state or score. Never shown to a fresh-context evaluator, to avoid anchoring it.
- **Bounded control:** may be the primary executor of a typed step, counting toward pass conditions and score; may route among permitted branches; may add requirements, such as extra verification or escalation to a human. It never replaces a human step, never executes a deterministic step, and never chooses outside the permitted set. Each use requires a documented fallback and must be reversible.

**Promotion** between modes is a human decision backed by evidence: thresholds derived on held-out local cases, calibration, error rates at the proposed coverage, agreement with agent evaluators and humans, disagreement slices, and latency on the hardware that hosts the engine. Compare the general English and typed-decisions English checkpoints on local cases instead of presuming either transfers; check input-length limits, question wording, and label sensitivity. Treat probabilities as model outputs, not proof that an action is safe. Model failure, timeout, or unavailability yields *not evaluated*, never *passed*.

**Hosting.** The engine owns the integration with Laya and Jev: DecisionEngine adapters live in the engine, and nothing else calls the models. Model inference is served next to the engine, most likely as a separate inference service, since running these models inside the JVM is unlikely to be practical. Benchmark on the actual deployment hardware before setting latency expectations; an Apple Silicon M5 Pro with 64 GB of memory is available for initial experiments. Jev can later be evaluated through the same contract on identical cases, comparing outcome quality, calibration, latency, cost, and operational fit.

## Reference workflow

Use a small SDLC as the test workflow, for example `intake → requirements → design → implementation → verification → release readiness → complete` with explicit rework edges. Its job is to exercise every framework feature at least once:

- A major checkpoint with a required deterministic step, a required scored reasoning step with a rubric, a reasoning step with a council of 3, an optional nice-to-have step, and a human step.
- A run that loses points to failed attempts and rework.
- At least one optional stage check and at least one transition with none.
- Deterministic steps at each evidence trust level, separation of duties on a human step, and a typed decision in each mode.
- A protected action, a rework loop, an excluded run, an override, concurrent users, and a definition version upgrade for new runs.

Keep it minimal. A second, deliberately non-SDLC toy workflow helps catch SDLC assumptions leaking into the engine.

## Observability, quality monitoring, and feedback

### Observability

Each driver kit configures its provider's telemetry export to a collector associated with the engine, starting with Claude Code's OpenTelemetry output. The engine joins telemetry with run events using the agent session ID recorded at session binding, within the session's time window on each run. Capture, where available: tokens by stage, agent, and skill, including evaluator subagents; latency; stage duration; retries; tool errors; test outcomes; checkpoint attempts and failures; rework loops; approval wait time; and human intervention. Record the provenance and collection method of each measure, make missing metrics explicit, and redact secrets and sensitive prompts according to policy.

### Quality monitoring

Quality monitoring tracks how runs perform over time and whether the run score, the workflow's own judgment of a run, can be trusted:

- **Trends:** run scores, step scores, failure and exclusion rates, rework, tokens, and duration, per step and per definition version.
- **Facts:** telemetry and events observed directly by the engine, kept separate from the score.
- **Outcomes:** downstream defects, acceptance, rework, human findings, and other later evidence, compared against run scores.
- **Evaluator agreement:** agent evaluators (by provider and model), Laya, and humans scoring the same steps, with disagreement and variance inspectable per step and rubric version.
- **Rubric health:** steps whose scores do not separate good and bad outcomes, bands that are rarely or always chosen, and steps with high council spread.

Sample runs for human review, especially excluded runs, overrides, and evaluator disagreements. Do not train or validate a step against the same evaluator's score as its only target.

### Feedback loop

1. Detect an underperforming stage, checkpoint, or step from score trends, objective metrics, and outcomes, reporting denominators, time window, data gaps, and task mix.
2. Collect representative high- and low-scoring runs and investigate causes, including task mix, tool failures, requirements ambiguity, or a flawed step or rubric.
3. Have a driver agent (initially Claude), in an interactive session, propose a hypothesis and a reviewable definition diff with predicted benefit, cost, and possible regressions. Never auto-apply it.
4. A human approves, rejects, or revises it in the UI. Accepted changes are published as a new definition version with the rationale attached.
5. Compare the new version against a baseline through shadow evaluation, staged rollout, or a controlled comparison, tracking score, quality, rework, latency, tokens, and human burden. Revert or revise when evidence does not support the hypothesis.

The output of this loop is a candidate definition version, not an instruction for an agent to remember next time.

## Delivery sequence and acceptance criteria

This document describes the target framework. Delivery starts with a PoC/MVP: a happy path through all three pillars, covering simple versions of milestones 1–8. The MVP is ready when a developer can drive the reference workflow with Claude Code to shipped code, with Laya taking part; the run is scored, observed through telemetry, and visible in quality monitoring; and the feedback loop turns run results into an approved, published definition version that is measured against its predecessor. Hardening, edge cases, and milestones 9–10 come afterwards as improvements. `BACKLOG.md` defines the happy path and separates what the MVP needs from what can wait. `PLAN.md` lays out the phases, starting with spikes and scaffolding.

| Milestone | Deliverable | Done when |
| --- | --- | --- |
| 1. Definition model | Schema for stages, transitions, evidence types and trust levels, checkpoints, steps, rubrics, scoring formula, typed decisions (with a null provider), protected actions; the reference workflow expressed in it; validation | A human can read the reference workflow, identify who authorizes each transition, and compute a sample run's score by hand; invalid definitions are rejected. |
| 2. Engine core | Java engine service; persistence for configuration, run state, and evidence; definition versions, runs, event log, checkpoint execution, scoring, replay, idempotency and leases, API, CLI, scoped authentication | Invalid or unapproved transitions cannot be committed; replays reconstruct the same state and score; a driver token cannot decide a human step. |
| 3. Driver integration | Remote MCP, driver contract, Claude Code driver kit (hooks, instructions, evaluator subagent, CLI), session binding, activity recording, evaluation tasks, protected action authorization | Claude Code drives the reference workflow end to end from a laptop on the subscription; reasoning steps are scored by a fresh-context evaluator that submits directly; the driver's activity is recorded; rejections list failed steps; protected actions fail closed. |
| 4. UI | Separately deployed UI over the engine API: definition view and publishing, run monitoring, score breakdown, human step inbox, overrides | Two users see the same live state; a human step can be decided only in the UI, with identity recorded; any run's score can be traced to its steps and evidence. |
| 5. Laya integration | DecisionEngine interface and Laya adapter in the engine, Laya inference service, shadow logging, human step assist, shadow step evaluation, transition recommendations, labeled local cases | Every Laya use produces logged shadow data comparable with the primary executor; any promotion is a published, evidence-backed decision. |
| 6. Observability | OTel ingestion and correlation, metrics, reports | A run's stages, tokens, and latency can be reconstructed without asking the driver agent; unavailable metrics are explicit. |
| 7. Evaluating the scoring | Outcome tracking, evaluator agreement, rubric health, human spot checks | Score validity can be inspected per step and rubric version; disagreement and missingness are visible. |
| 8. Feedback loop | Detection, investigation, diff proposal, approval, experiment tracking | A definition version can be compared with its predecessor and reverted with its rationale intact. |
| 9. Provider comparison | Jev or other providers through the same contract | Evidence supports or rejects each provider for each use separately. |
| 10. Additional drivers | A second driver kit for another coding-agent provider through the same contract | The second provider runs the reference workflow without engine changes; its capability gaps are declared and enforced by the engine. |

## Design guardrails

- Prevent concurrent or repeated transition requests from double advancing a run: atomic state updates, idempotency keys, driver leases.
- Validate evidence at decision time and record which revision and trust level were checked.
- Fail closed: an unreachable engine, stale state, or unavailable evaluator or decision provider never lets a protected action or required step pass.
- Distinguish a step that is *not evaluated* from one that *failed* or *passed*, and an *excluded* run from a low-scoring one.
- The engine alone applies thresholds and computes points; evaluators only report raw results.
- A reasoning step's evaluator never sees the working conversation, never wrote the work, and never receives advisory outputs that could anchor it.
- Keep human approval identity outside the model's control; driver credentials never carry approval scopes; override rights are explicit.
- Version independently: workflow definitions, driver kit, and prompts. Rubrics, scoring formulas, and decision questions are versioned as part of the definition version; a rubric's version is the definition version where it last changed.
- Keep SDLC concepts out of the engine; they live only in workflow definitions. Keep provider concepts out of the engine; they live only in driver kits.
- Treat the developer's machine as trust-only: do not rely on enforcing isolation there, but record everything the driver does so it can be audited.
- Retain enough provenance to audit decisions while redacting secrets and limiting access to sensitive code and prompts.
- Start with the reference workflow and a small number of meaningful checkpoints. Extend only after the engine and its scoring are trustworthy.

## Open decisions

Open implementation questions are tracked in `BACKLOG.md`, prioritized for a happy-path MVP first.

## References to verify during implementation

- Claude Code documentation: [subscription sign-in](https://code.claude.com/docs/en/quickstart), [MCP, including remote servers](https://code.claude.com/docs/en/mcp), [hook decision control](https://code.claude.com/docs/en/hooks), [subagents](https://code.claude.com/docs/en/sub-agents), [plugins](https://code.claude.com/docs/en/plugins), [OpenTelemetry monitoring](https://code.claude.com/docs/en/monitoring-usage).
- Other driver providers: before writing a kit, verify each candidate's support for MCP, tool-call hooks, fresh-context subagents or sessions, transcripts, and telemetry export.
- MCP Java SDK: [server support and transports](https://github.com/modelcontextprotocol/java-sdk), including its Spring integration if Spring is chosen.
- Laya project: [typed decisions, calibration guidance, and current limitations](https://github.com/NandhaKishorM/laya).
- Jev: [System One model description](https://typesafe.ai/blog/introducing-system-one-models-and-jev).

This file expresses the desired framework. The MVP questions in `BACKLOG.md` are decided when development starts; everything else is picked up as the happy path is extended.
