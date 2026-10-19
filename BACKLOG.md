# Backlog

The PoC/MVP goal is one working happy path through all three pillars: execution, observability and quality monitoring, and the feedback loop. Laya is part of the MVP. When the happy path works end to end, the MVP is ready; everything after that is an improvement.

MVP items come with a suggested default to confirm or replace. Everything else stays on record but is not a priority.

## The happy path

### Pillar 1: Execution (build and ship code)

1. The engine runs with the reference workflow loaded.
2. A developer starts Claude Code with the driver kit and starts a run on a real repository.
3. Claude works through the stages, writes the code, pushes the branch, submits evidence, and requests transitions.
4. The major checkpoint runs a deterministic check in the runner at the pushed commit, a reasoning step scored by a fresh-context Claude evaluator in the runner, and a human step approved in the UI.
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
- [x] **Definition format:** *Decided (spike 003):* one self-contained YAML file per definition version, carrying `schema: ai-workflow/v0` and the workflow ID; the engine numbers versions when a human publishes them. Validated in two layers: a published JSON Schema 2020-12 for structure, and semantic rules in the engine. Evidence types may embed a JSON Schema for their payload. The initial version is loaded from a file; later versions come from feedback-loop proposals approved in the UI. No general UI editor yet. See `docs/definitions/schema.md` and `specs/003-definition-model-draft/`.
- [x] **Deterministic steps:** *Decided (spike 003):* three built-in check types that run in the engine over evidence, `evidence_exists`, `field_compare` (eq, ne, lt, lte, gt, gte against a literal), and `min_items`; and one runner check type, `command`, a command in a sandbox image that passes on exit code 0. One check per step, and each step states `runs_in`. No expression language. See `docs/definitions/schema.md`.
- [ ] **MCP server:** *Suggested:* MCP Java SDK over HTTP with a bearer token.
- [ ] **Auth:** *Suggested:* one static driver token per user, and a simple UI login. Human decisions and definition approvals are accepted only from the UI login, even in the MVP.
- [ ] **UI technology:** *Suggested:* whatever is fastest to build. Pages refresh by polling.
- [ ] **Hooks to engine:** *Suggested:* hooks call the engine's HTTP API with a small script. No dedicated CLI yet.
- [ ] **Result delivery to the driver:** *Suggested:* polling `checkpoint_status`.
- [ ] **Runs and repositories:** *Suggested:* one active run per workflow and subject key; the SDLC driver kit uses repository + branch as the subject (spike 001, Q1). Shipping means merging that branch.
- [ ] **Evidence:** *Suggested:* the driver submits small inline payloads, such as design notes and the commit SHA, marked driver-reported. Runner checks produce engine-verified evidence, such as the test summary at that SHA. *Partly decided (spike 003):* runner checks produce no evidence in the MVP; their log, runner, image, command, and revision are provenance only, and built-in checks read only driver-submitted evidence (see Next).
- [ ] **Activity recording:** *Suggested:* a post-tool-use hook sends tool name and outcome. No transcripts yet.
- [x] **Evaluator location:** *Decided (intent review 2026-10-18):* reasoning steps run in the runner by default. Driver-side evaluation stays in the driver contract as an optional capability, but the MVP does not build the evaluator subagent (see Next).
- [x] **Scoring:** *Decided (spike 003):* linear raw-to-points mapping; councils of an odd size, aggregated by median. Workflow-level deductions only, subtracted from the normalized 0–100 score: one per failed attempt of a required step (even if overridden) and one per rework traversal, both paid when they coincide (5 points each in the reference workflow). An attempt that fails only because a required step was *not evaluated* costs nothing. An overridden step earns the points its raw score maps to; a human rejection cannot be overridden. A worked run is in `docs/definitions/example-run.md`.
- [x] **Stage tasks:** *Decided (spike 001):* each non-terminal stage has a stage task (templated task, acceptance criteria, tests) with provider-neutral limits (iterations, wall time per visit, consecutive failed completion checks). The driver kit enforces the limits and the engine records each iteration. Exhaustion always escalates to the owner in the UI; other stops only pause. See `specs/001-concepts-what-a-workflow-is/`.
- [x] **Reference workflow:** *Decided (spike 003):* `requirements → implementation → verification → release → complete`, with a failure terminal `discarded` and one rework edge `verification → implementation`. The major checkpoint before `release` has `diff_present` (engine), `tests_green` (a runner `command` check), `code_quality` (a runner evaluator with a council of 3, shadowed by Laya), an optional runner evaluator, and a human step with separation of duties; a stage check before `complete`. The `verification` stage task runs the tests locally for feedback only. The merge is the protected action, authorized only at the revision the checkpoint reviewed. See `docs/definitions/sdlc-reference.yaml`.

### Runner

Decided in the 2026-10-18 intent review (constitution amendment log): gate checks run server side, so the driver only builds.

- [x] **Isolation:** *Decided:* a separate runner service next to the engine, called only by the engine, that starts a throwaway sandbox per job. Sandboxes hold no engine or database credentials.
- [x] **Code source:** *Decided:* the driver pushes the branch and names the commit SHA in the transition request; the runner clones that SHA with a read-only token. Unpushed work is never evaluated.
- [x] **Trust level:** *Decided:* runner results are engine-verified, with the runner, image, command, and SHA as provenance.
- [x] **Model access:** *Decided:* the runner's Claude Code evaluator runs headless with the owner's subscription token (`claude setup-token`) while the owner is the only person using the deployment (one person's several UI accounts count as one). A second user needs per-user credentials or an API key first (see Next).
- [x] **Unavailability:** *Decided:* a runner job that cannot run leaves its step not evaluated, failing closed. No automatic fallback to driver-side evaluation.
- [ ] **Sandbox technology:** *Suggested:* the runner creates one Kubernetes Job per runner job, from the image the step declares, with resource limits and a deadline, in a sandbox namespace that a network policy restricts. Settled by spikes 9 and 10. Input from spike 2 (G4): sandbox pods are never privileged; services such as a database run as native sidecars gated by a startup probe, so a service that never starts means the job did not run; egress is limited to an in-cluster Maven proxy; a check may take up to 15 minutes.
- [x] **Check environment in definitions:** *Decided (spike 003):* a `command` check declares its image inline, pinned by digest; `run` as an argument list; literal `env` (no secrets); `services`, each with a `ready` command, reached at `localhost`; and a required `timeout` covering the whole job. Running past the timeout fails the check; a job that cannot run (runner down, revision not fetchable, sandbox or service never ready) leaves it *not evaluated*. See `docs/definitions/schema.md`.

### Laya

- [ ] **Runtime and serving:** Laya's requirements (language, hardware, memory, input limits) and how it is served next to the engine. *Suggested:* a separate local inference service with a minimal HTTP API, called by the engine's DecisionEngine adapter.
- [ ] **MVP use:** *Suggested:* one typed question in shadow mode at the major checkpoint, classifying the reasoning step into its rubric bands so it can be compared with the Claude evaluator and the human. Shown in the quality view, not on the approval screen.
- [ ] **Model checkpoint:** general English or typed-decisions English. *Suggested:* try both on a handful of local cases and keep the better one.
- [ ] **Hardware:** *Suggested:* the M5 Pro for the MVP; measure latency there.

### Observability and quality monitoring

- [ ] **Telemetry pipeline:** *Suggested:* an OpenTelemetry Collector that forwards to the engine, which stores only what it needs (tokens, cost, duration, tool results, errors) keyed by session ID. A Grafana-style stack is the alternative if raw exploration is needed.
- [ ] **Session correlation:** *Suggested:* the session-start hook registers the Claude Code session ID with the run; confirm that this ID is present in the telemetry.
- [ ] **Quality view:** *Suggested:* one UI page with score trends per step and per definition version, failure and rework rates, tokens and duration per run, and evaluator agreement. Input from spike 3: show each test-bed task's hidden acceptance tests as an outcome measured after the run, next to its score, never part of it.

### Feedback loop

- [ ] **Detection:** *Suggested:* a simple rule over the last N runs, such as the step with the lowest average normalized score or the highest failure rate. Input from spike 2: the v1 reference workflow has no planted weakness; if no weakest step stands out after the first seed runs, consider planting one before the v2 cycle.
- [ ] **Proposal flow:** *Suggested:* a skill or slash command in the driver kit that fetches the analysis from the engine, has Claude draft a YAML diff with a hypothesis, and submits it as a draft version.
- [ ] **Approval:** *Suggested:* a UI page showing the diff and hypothesis, with approve and reject; approval publishes the new version.
- [ ] **Comparison:** *Suggested:* the quality view splits metrics by definition version. With few runs, show raw numbers and run counts rather than claim significance.
- [x] **Seed data:** *Decided (spike 002):* tasks T01–T12 in `sample-crm`, run in order, with T01, T03, T04, T05, T06, and T11 rerun on the new definition version. See `docs/test-bed.md`. The baseline exists: tag `baseline` at `0916dbf` (spec 004). Still to do: draft each task's record (expected result and hidden acceptance tests) in a session separate from any run, for the user to review, kept in `docs/`.

### Local setup

- [x] **Infrastructure:** *Decided (user, 2026-10-18):* server-side components run on Kubernetes: a local cluster for development and the MVP, and GCP (GKE) later. The same manifests should serve both.
- [ ] **Local cluster:** *Suggested:* a local Kubernetes cluster running the engine, UI, database, runner, OTel Collector, and Laya inference service, with the Laya service possibly native on the host for the Apple GPU. Settled by spike 10.

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
- [x] **2. Test bed: target repository and task set.** *Done:* `specs/002-test-bed-target-repository-and-task-set/`, with the test bed in `docs/test-bed.md` (reopened and closed again on 2026-10-18 for the runner sandbox toolchain, G4). Choose where runs do real work and which tasks they do, so that the other spikes, the seed data, and the Phase 4 validation all use the same cases. Questions to answer:
  - Which sandbox repository to use, ideally one you own so that merging to it is harmless, and whether it needs CI for the later system-attested evidence.
  - Which toolchain the runner's sandbox image needs to build and test it, and how its Testcontainers-based tests get a database inside a Kubernetes sandbox pod (spike 2, G4).
  - Which 8–12 small tasks to use, with a mix of difficulty, including some that should fail a checkpoint or need rework, and the expected result for each.
  - Which of these become Laya's first labeled local cases.

  Deliverable: the repository and a task list with expected results in `docs/`. Depends on: nothing.

### Wave 2: Unknowns

- [x] **3. Definition model draft.** *Done:* `specs/003-definition-model-draft/`, with the schema, reference workflow, and example run in `docs/definitions/`, including the runner inputs below. Write the reference workflow in a draft YAML schema with stages, transitions, evidence, checkpoints, rubrics, scoring, deductions, Laya's typed question, and the protected action. Compute one example run's score by hand. Deliverable: the draft schema and example, ready to become milestone 1. Depends on: 1. Settles: Definition format, Deterministic steps, Scoring, Reference workflow. Input from spike 1: express its decisions in the schema, including stage tasks and their limits, and walk one run with a failed attempt, a rework loop, and an override. Input from the intent review: where each step runs (engine, runner, driver) and how a runner check declares its image and command. Input from spike 2 (G4): runner checks also declare service containers, and spike 3 decides how hidden acceptance tests get into the sandbox. Input from spike 2: test-bed tasks T05 (rework), T06 (failed checkpoint), and T07 (override) in `docs/test-bed.md` are ready-made cases for that run.
- [ ] **4. Claude Code driver feasibility.** Confirm that the driver design works with Claude Code on a subscription:
  - Claude Code connects to a remote MCP server written with the MCP Java SDK.
  - A session-start hook can register the session ID with the engine.
  - A pre-tool-use hook can block a merge based on an engine response, and a post-tool-use hook can report activity.
  - Headless Claude Code runs in a Linux container as a runner-side evaluator: authenticated with a `claude setup-token` token, read-only tools, a fresh session per task, and the result as structured JSON output.
  - All of it can be packaged as a plugin.
  - A full run, including runner evaluations alongside the developer's own session, fits within subscription usage limits.
  - Lower priority, since the MVP does not need it: an evaluator subagent with a fresh context and read-only tools can fetch a task and submit its result through MCP without the main agent relaying it.

  Deliverable: a write-up of what works, what doesn't, and workarounds, with a throwaway prototype. Depends on: nothing; run it alongside 5, which can reuse the same prototype. Settles: MCP server, Hooks to engine, Activity recording, and the runner evaluator's feasibility. Input from spike 1: prototype a stage task driven by a Stop hook (does the 8-block cap fire despite progress; does Stop fire on Esc), an `http` Stop hook against a stub (block continues, failure lets Claude stop), and `/goal` with a turn limit in its condition. Known constraint: a plugin cannot raise the 8-block cap. Input from spike 2: pilot test-bed tasks T01, T05, and T06 in plain Claude Code to see whether the planted failures fire and how much usage each task takes (Max 5x).
- [ ] **5. Claude Code telemetry.** Find out what Claude Code's OpenTelemetry export actually contains: metrics and events, session ID, tokens, cost, tool results, and how it is configured from a plugin or environment. Deliverable: a write-up listing what the quality view can and cannot show. Depends on: nothing; shares a prototype with 4. Settles: Telemetry pipeline, Session correlation.
- [ ] **6. Laya/Jev role in the workflow.** Work out where typed decision models actually add value, before the Laya MVP items are settled; their suggested defaults are placeholders until then. Questions to answer:
  - What Laya and Jev are in practice: inputs, outputs, confidence semantics, input limits, runtime and hardware needs, licensing, and availability (local or hosted).
  - Which roles are worth it: human step assist, typed step evaluation, transition guidance, and run categorization for the feedback loop.
  - Where in the reference workflow each role fits, and how it relates to the Claude evaluator and the human at the same point.
  - Which typed questions and label sets to start with, and how to label the first local cases.
  - Whether Laya and Jev overlap or complement each other.

  Deliverable: a short write-up recommending Laya's MVP use and the order of later roles, tried on a few local cases from the test bed. Depends on: 1, 2. Settles: all Laya items. Input from spike 2: the first cases are hand-made good and flawed variants of T01, T03, T04, T06, and T11 (ten cases), labeled by the user blind to the Claude evaluator; real-run outputs come later as the held-out set. Token-count a case packet first, since `laya-typed-decisions` has a 1024-token context and the sandbox is Spring Boot. Input from spike 3: the reference workflow has one shadow-mode typed question, `code_quality_band`, attached to the `code_quality` step, with that step's rubric band IDs as labels; change it here if this spike decides otherwise.

### Wave 3: Shape

- [ ] **7. Target architecture.** Combine the results of waves 1 and 2 into the system's shape, marking clearly what the MVP builds and what is only the target. Questions to answer:
  - Components and their boundaries: engine modules (definitions, runs and state, checkpoint execution, scoring, evaluation tasks, runner dispatch, DecisionEngine adapters, telemetry ingestion, driver API, UI API), UI, runner (with its source and evaluator adapters), Laya service, collector, database, and driver kit.
  - The runner interface: the job packet, how results come back, timeouts, and how the engine tells a runner job that failed to run from a check that failed.
  - Sequences for each happy path step: start and bind, submit evidence, request a transition, run a checkpoint with runner checks, runner-side reasoning, human, and Laya steps, authorize a protected action, correlate telemetry, and propose and publish a definition version.
  - How long-running checkpoints progress: what moves a checkpoint forward while it waits hours for a human, and how the driver and UI learn about it.
  - Persistence: state tables, the event table, what can be replayed, and how requests on the same run are serialized and made idempotent.
  - Trust boundaries and auth: what the agent on the laptop can reach, how driver tokens and UI sessions are kept apart, what the runner and its sandboxes can reach and which credentials they hold (repository read token, subscription token), and which risks the MVP accepts.
  - Where provider-neutrality and SDLC-neutrality are enforced in the module structure.

  Deliverable: an architecture document with component and sequence diagrams, plus a short decision record for each key choice. Depends on: 1, 3, 4, 5, 6. Settles: State and events, Auth, Result delivery to the driver, Runs and repositories, Evidence. Input from spike 1: how an open checkpoint attempt progresses while the driver waits on a human; how exhaustion escalations reach the owner, and what "owner directs a rework" does mechanically. Input from spike 3: what a council does when one member's runner job cannot run, and how the runner reports "could not run" (step *not evaluated*) apart from "failed" (non-zero exit or timeout). Input from article 9 (no orchestrator): the engine is reactive and never pushes work to drivers. A checkpoint waiting on a human moves forward when the engine records the decision; the driver and the owner learn about it, and about exhaustion escalations, by pulling `status` or `checkpoint_status`, not by a coordinator pushing work.
- [ ] **8. Driver API contract.** *(Suggested addition; can be folded into 7 if it stays small.)* Define the provider-neutral boundary that every driver kit is written against. Questions to answer:
  - The input and output schemas of each MCP operation, and which ones the MVP needs.
  - The HTTP equivalents that hooks call, and how they are authenticated.
  - The format of denial messages, idempotency keys, and the evaluation task packet (step, rendered rubric, evidence references).
  - Which driver capabilities a session declares, and how the engine checks them.

  Deliverable: the operation list with draft schemas, and one happy path run written as a sequence of calls. Depends on: 3, 4, 7. Input from spike 1: who computes the subject key, and how iterations and exhaustion escalations are reported. Input from spike 3: how the subject key is derived from the definition's `run_inputs`, and the evaluation task packet built from a step's `instructions`, rubric, and inputs, including whether a runner evaluator reads the driver's `diff` or diffs its own clone. Input from article 9 (no orchestrator): `status` must carry everything a generic driver needs to choose its next action (the stage task, the permitted transitions and their checkpoints, pending evaluation tasks, pending human steps, and recommendations), so that kit instructions hold no workflow-specific steps.

### Wave 4: Tools

- [ ] **9. Technology stack.** Choose the technology for each component in the architecture. Questions to answer:
  - Engine: Java version, Spring Boot or a lighter framework, and how each fits the MCP Java SDK's server and transport support; build tool.
  - Database, migrations, and YAML parsing and validation for definitions.
  - UI: a single-page app or server-rendered pages, given that the UI is deployed separately and should be fast to build.
  - Laya serving: language and inference runtime, based on spike 6, and whether it can use the Apple GPU.
  - Runner: language, and how it starts sandboxes as Kubernetes Jobs or pods (client library, and a service account allowed only in the sandbox namespace). Input from spike 2 (G4): Jobs carry native sidecars for services, which needs Kubernetes 1.33 or later.
  - OTel Collector distribution, the hook script language (what is reliably installed on a developer laptop), and the test approach (for example, Testcontainers).

  Deliverable: a stack table with the choice, the reason, and the alternative rejected for each component, plus a hello-world through the chosen framework and MCP SDK. Depends on: 6, 7 (and 8 for the MCP and HTTP layer). Settles: Engine framework, Database, UI technology, Laya runtime and serving, Sandbox technology.

### Wave 5: Running it

- [ ] **10. Local setup.** Design how the whole system runs on a local Kubernetes cluster on one laptop, for development and for the MVP, in a way that carries over to GKE later. Questions to answer:
  - Which local cluster (for example kind, k3d, minikube, or the cluster built into Docker Desktop or OrbStack), and how manifests are packaged (plain manifests, Kustomize, or Helm) so that one set serves the local cluster and GKE.
  - What runs in the cluster and what runs natively. Containers on macOS generally cannot use the Apple GPU, so the Laya service may need to run on the host.
  - How the runner starts sandbox pods (namespaces, RBAC, network policy, resource limits), and how it gets the repository read token and the `setup-token` subscription token as Kubernetes secrets without exposing them to sandboxes running subject code.
  - Networking: how Claude Code on the host reaches the engine's MCP endpoint and HTTP API (ingress or port forwarding), and how its telemetry exporter reaches the collector.
  - How to install the driver kit plugin from the local repository and configure its token.
  - Bootstrapping: creating a user and driver token, loading the reference workflow, seeding data, and resetting to a clean state.
  - The development loop: running the engine and UI from the IDE with hot reload while the other components run in the cluster, or a tool that syncs code into the cluster.

  Deliverable: draft Kubernetes manifests and a README section that takes a fresh clone to a stub `status` call from Claude Code. The Phase 0 scaffolding then implements it. Depends on: 9. Settles: Local cluster, Sandbox technology. Input from spike 2 (G4): the Mac has no container runtime yet, so pick one together with the cluster (Testcontainers actively supports only Docker Desktop and Docker on Linux); run an in-cluster Maven proxy as the sandboxes' only egress; decide where the runner image is built and pushed. Input from spike 3: bootstrapping creates two human UI users, an owner and an approver, because the reference workflow's human step requires separation of duties. Also: build the `sample-crm` check image and set its digest and the PostgreSQL image digest in `docs/definitions/sdlc-reference.yaml`; its database variables (`SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`) must match what spec 004's plan reads.

## Next: improvements after the MVP

- [ ] **Laya beyond shadow mode:** advisory gate assist on the approval screen, transition recommendations, and promotion based on evidence.
- [ ] **Human step scoring:** the approver picks a rubric band in the UI.
- [ ] **Definition authoring** in the UI, beyond feedback-loop proposals.
- [ ] **System-attested evidence** from one CI or git hosting integration.
- [ ] **Runner-produced evidence:** runner checks produce engine-verified evidence (such as a test summary at the SHA) that built-in checks can read, with a `min_trust` key on step inputs or a source on each evidence type so a driver cannot supply it (spike 003, Q23, Q24).
- [ ] **Real identity:** identity provider, roles, driver token expiry and revocation.
- [ ] **Multi-user robustness:** driver leases with heartbeat and takeover.
- [ ] **Evaluation tasks and runner jobs:** timeouts and retries when an evaluator never submits or a runner job hangs.
- [ ] **Runner performs protected actions:** the engine has the runner do the merge with write credentials the laptop never holds, making protection authoritative instead of hook-based.
- [ ] **GCP deployment:** run the same manifests on GKE, and choose the GCP pieces: cluster mode (Standard or Autopilot), managed database, secrets, ingress, and where Laya runs without the Apple GPU.
- [ ] **Runner model access for several users:** per-user subscription tokens or an API key, decided before a second user joins.
- [ ] **Driver-side evaluator subagent:** the optional Evaluation capability in the Claude Code kit, for steps that declare driver-side evaluation.
- [ ] **Transcripts:** upload, with secret redaction.
- [ ] **Dedicated CLI:** for humans and hooks. Startup latency matters, so possibly a native image or a non-JVM client.
- [ ] **Driver kit distribution and updates.**
- [ ] **Step-level deduction overrides.**
- [ ] **Richer feedback loop:** investigation across representative runs, predicted cost and regressions, revert flow. A first candidate change: have runs use `sample-crm`'s own spec kit in the requirements stage (spike 003, Q29).
- [ ] **Score validity:** outcome sources (defects, acceptance) and human spot-check sampling.
- [ ] **Non-SDLC toy workflow**, to catch SDLC assumptions in the engine. A paper sketch (article publication) is in `docs/concepts/model.md`; write it in the draft schema from spike 003 (`docs/definitions/schema.md`) including a runner `command` check, to check that no SDLC or Kubernetes term leaks into the schema.
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
