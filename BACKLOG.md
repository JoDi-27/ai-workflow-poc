# Backlog

The PoC/MVP goal is one working happy path through all three pillars: execution, observability and quality monitoring, and the feedback loop. Jev is the MVP's only typed decision provider; Laya is out of the MVP until it can be trained (spike 010, Q27). When the happy path works end to end, the MVP is ready; everything after that is an improvement.

MVP items come with a suggested default to confirm or replace. Everything else stays on record but is not a priority.

## The happy path

### Pillar 1: Execution (build and ship code)

1. The engine runs with the reference workflow loaded.
2. A developer starts Claude Code with the driver kit and starts a run on a real repository.
3. Claude works through the stages, writes the code, pushes the branch, submits evidence, and requests transitions.
4. The major checkpoint runs a deterministic check in the runner at the pushed commit, a reasoning step scored by a fresh-context Claude evaluator in the runner, and a human step approved in the UI.
5. Jev answers its typed question at that checkpoint, in shadow mode, and the result is recorded next to the Claude evaluator's and the human's.
6. Shipping (for example, merging to the main branch) is a protected action that the engine authorizes through a hook.
7. The run completes, and the UI shows its score and breakdown.

### Pillar 2: Observability and quality monitoring

8. Claude Code's OpenTelemetry output reaches the collector, and the engine correlates it with the run by session ID.
9. The UI shows per-run telemetry (tokens, duration per stage, tool errors) next to the run's events and score.
10. A quality view shows trends across runs: run and step scores, failure and rework rates, and agreement between the Claude evaluator, Jev, and the human.

### Pillar 3: Feedback loop

11. From the results of several runs, the analysis service flags the weakest step or checkpoint.
12. In an interactive Claude Code session, Claude reads the analysis from the analysis service and proposes a definition change with a hypothesis.
13. A human reviews the diff and approves it in the UI, and the engine publishes it as a new definition version.
14. New runs use the new version, and the quality view compares it with the previous one.

## MVP: decide to build the happy path

### Execution

- [x] **Engine framework:** *Decided (spike 012, Q1–Q6, Q13):* Java 25 LTS and Spring Boot 4.1, one Maven multi-module build with the Wrapper; separate modules for the core, the ports, and each adapter, plus ArchUnit rules; Spring annotations allowed in the core (AD-20 refined). Data access through Spring Data JPA / Hibernate. The HTTP API is Spring MVC, with RFC 9457 denials through `ProblemDetail`, written schema-first: hand-written JSON Schema 2020-12 files in an OpenAPI 3.1 document, no code generation, networknt contract tests. Every server-side service (engine, MCP gateway on MCP Java SDK 2.1.0 stateful with one replica, runner with fabric8 kubernetes-client 8.x, analysis service) is Java on Spring Boot. Tests use JUnit 5 and Testcontainers. A hello-world connected Claude Code through the gateway to a stub engine. See `specs/012-technology-stack/`. *Earlier, suggested:* Spring Boot, for speed of setup.
- [x] **Database:** *Partly decided (spike 010, Q22):* one relational database with ACID transactions across tables, row-level locks, and unique constraints, and document columns for definition YAML and parsed form, event payloads, and evidence payloads; not MongoDB. *Decided (spike 012, Q7):* PostgreSQL 18, `jsonb` document columns, Flyway SQL migrations with one set per schema, and Hibernate `ddl-auto=validate`; definitions parsed with Jackson 3 YAML (YAML 1.2, duplicate keys rejected) and validated with networknt json-schema-validator 3.x. A trial confirmed the run-row lock (Hibernate emits `FOR NO KEY UPDATE`), `SKIP LOCKED` outbox claims, `jsonb` mapping, and the one-active-run partial unique index. See `specs/012-technology-stack/`. *Earlier, suggested:* one relational database. Small evidence payloads and telemetry are stored in it directly, with no object storage yet.
- [x] **State and events:** *Decided (spike 010, Q5, Q16):* state tables are the source of truth, with append-only events in the same transaction; replay is a check that rebuilds state and score from events. Each request locks the run row; every mutating call and human decision takes an idempotency key; runner dispatch and event publication go through a transactional outbox. See `docs/architecture/` (AD-5, AD-16). *Earlier, suggested:* state tables plus an append-only event table, written in the same transaction.
- [x] **Definition format:** *Decided (spike 003):* one self-contained YAML file per definition version, carrying `schema: ai-workflow/v0` and the workflow ID; the engine numbers versions when a human publishes them. Validated in two layers: a published JSON Schema 2020-12 for structure, and semantic rules in the engine. Evidence types may embed a JSON Schema for their payload. The initial version is loaded from a file; later versions come from feedback-loop proposals approved in the UI. No general UI editor yet. See `docs/definitions/schema.md` and `specs/003-definition-model-draft/`.
- [x] **Deterministic steps:** *Decided (spike 003):* three built-in check types that run in the engine over evidence, `evidence_exists`, `field_compare` (eq, ne, lt, lte, gt, gte against a literal), and `min_items`; and one runner check type, `command`, a command in a sandbox image that passes on exit code 0. One check per step, and each step states `runs_in`. No expression language. See `docs/definitions/schema.md`.
- [x] **MCP server:** *Refined (spike 010, Q20):* runs as its own stateless MCP gateway Deployment that calls the engine's HTTP driver API with the caller's driver token; the engine itself serves HTTP only. *Decided (spike 005):* MCP Java SDK (2.1.0 tried), stateful Streamable HTTP servlet transport, with a static bearer driver token. Claude Code connected and called its tools; a wrong token gives 401 and the server is dropped. MCP calls do not carry Claude Code's session ID, so tools take the subject as an argument. See `specs/005-claude-code-driver-feasibility/`. *Refined (spike 011, Q2, Q3, Q8):* the MVP tools are `status`, `start_run`, `request_transition`, `get_definition`, `submit_draft`, and `get_analysis`, each a thin mirror of the HTTP driver API under `/v1`. The kit's PreToolUse hook injects `session_id` into their arguments with `updatedInput` (to confirm when the kit is built). The gateway derives the idempotency key from a hash of the user, tool, and arguments, and mutating tools echo `expected_attempts` from status. Denials come back as `isError: true` results. See `docs/driver-api/`.
- [x] **Auth:** *Decided (spike 010, Q4, Q14, Q15, Q20):* one static driver token per user (stored hashed), accepted on hooks, the MCP gateway (passed through unchanged), and OTLP; scopes cover the user's own leased runs, reading analysis, and submitting drafts, never approvals. The engine owns human login and issues sessions; the UI holds no credential that can approve. See `docs/architecture/` (AD-4, AD-14, AD-15, AD-21). *Earlier, suggested:* one static driver token per user, and a simple UI login. Human decisions and definition approvals are accepted only from the UI login, even in the MVP.
- [x] **UI technology:** *Partly decided (spike 010, Q4, Q26):* a static client of the engine's UI API and the analysis API; it shows the change through the definition's subject view, read in the browser from the git host. *Decided (spike 012, Q9):* a static single-page app in React 19.2, Vite 8, and TypeScript, tested with Vitest. See `specs/012-technology-stack/`. *Earlier, suggested:* whatever is fastest to build. Pages refresh by polling.
- [x] **Hooks to engine:** *Decided (spike 005):* command hooks only (SessionStart cannot be `http`, and `http` hooks cannot read the plugin's token), calling the engine's HTTP API through one small script. The merge gate is a PreToolUse hook on every tool with `onFailure: "block"` and a short timeout; the engine reads the full command, merges go only through `gh pr merge`, and GitHub branch protection stays the real gate. When the engine is unreachable, every tool call is blocked (user, Q6). Stage tasks use a command Stop hook whose iterations the engine counts. See `specs/005-claude-code-driver-feasibility/`. Input from spike 006, to check when the kit is built: in the capture, a subagent's hand-back arrived as a new `user_prompt` and the Stop hook fired while the subagent was still running, so the Stop-hook stage task must not count that stop as a finished iteration; and `/clear` (not tried by spikes 005 or 006) should give a new session ID with SessionStart `source=clear`, which the kit binds to the run. *Refined (spike 010, Q3):* the kit's hook script maps a tool call to a neutral action name and revision; the engine no longer reads the full command. *Refined (spike 011, Q4, Q8, Q11):* four hook endpoints: register (`POST /v1/sessions`, which binds and returns a compact `summary` for SessionStart), gate (every PreToolUse, always 200 with `allow` or `deny` and a structured denial), activity (async), and iterations (Stop, where the engine returns `continue` or `stop`). The engine answers in neutral JSON, and the kit maps it to Claude Code's hook output. See `docs/driver-api/`.
- [x] **Result delivery to the driver:** *Decided (spike 010, Q2, Q7):* pull only. The driver reads `status` or `checkpoint_status`; at a human gate the session ends, and a later session resumes from the status injected at SessionStart. Nothing pushes to drivers (Art. 9). See `docs/architecture/` (AD-7). *Refined (spike 011, Q6, Q7, Q10, Q11):* `checkpoint_status` folds into `status`, which lists startable workflows when there is no run, shows checkpoints with their rubrics and thresholds, and after a failure gives each step's reason, a trimmed log tail, and council bands and rationales (no raw scores). SessionStart injects a compact summary under Claude Code's 10,000-character cap. See `docs/driver-api/`.
- [x] **Runs and repositories:** *Decided (spike 010, Q14, Q16, Q26):* one active run per workflow and subject key, enforced by a unique constraint; binding a session acquires the run's lease for its user; the transition request carries the revision, and the runner reads the subject at it. *Refined (spike 011, Q1):* the definition's `subject.key` names the run inputs that form the subject key, and the engine derives it; the kit only sends run inputs. *Earlier, suggested:* one active run per workflow and subject key; the SDLC driver kit uses repository + branch as the subject (spike 001, Q1). Shipping means merging that branch.
- [x] **Evidence:** *Decided (spike 010, Q26):* git is the evidence channel. A transition request carries only the revision, and the reference workflow needs no driver-submitted evidence: the runner reads the subject at that revision for checks, evaluators, and typed-decision input packets; requirements are a committed file; the UI shows the change through a declared subject view, read in the browser from the git host. `submit_evidence` stays for workflows that do not use git. See `docs/architecture/` (AD-27). *Earlier, suggested:* the driver submits small inline payloads, such as design notes and the commit SHA, marked driver-reported. Runner checks produce engine-verified evidence, such as the test summary at that SHA. *Partly decided (spike 003):* runner checks produce no evidence in the MVP; their log, runner, image, command, and revision are provenance only, and built-in checks read only driver-submitted evidence (see Next).
- [x] **Activity recording:** *Decided (spike 005):* an async PostToolUse command hook sends the tool name, input, outcome, session ID, and agent type (so subagent calls are recorded apart). No transcripts yet. See `specs/005-claude-code-driver-feasibility/`.
- [x] **Evaluator location:** *Decided (intent review 2026-10-18):* reasoning steps run in the runner by default. Driver-side evaluation stays in the driver contract as an optional capability, but the MVP does not build the evaluator subagent (see Next).
- [x] **Scoring:** *Decided (spike 003):* linear raw-to-points mapping; councils of an odd size, aggregated by median. Workflow-level deductions only, subtracted from the normalized 0–100 score: one per failed attempt of a required step (even if overridden) and one per rework traversal, both paid when they coincide (5 points each in the reference workflow). An attempt that fails only because a required step was *not evaluated* costs nothing. An overridden step earns the points its raw score maps to; a human rejection cannot be overridden. A worked run is in `docs/definitions/example-run.md`.
- [x] **Stage tasks:** *Decided (spike 001):* each non-terminal stage has a stage task (templated task, acceptance criteria, tests) with provider-neutral limits (iterations, wall time per visit, consecutive failed completion checks). The driver kit enforces the limits and the engine records each iteration. *Refined (spike 011, Q4, Q5):* the engine counts iterations and returns the continue-or-stop verdict on each Stop; a failed completion check is the agent's self-check with unmet criteria, reported with the iteration. Exhaustion always escalates to the owner in the UI; other stops only pause. See `specs/001-concepts-what-a-workflow-is/`.
- [x] **Reference workflow:** *Decided (spike 003):* `requirements → implementation → verification → release → complete`, with a failure terminal `discarded` and one rework edge `verification → implementation`. *Updated (spike 010, Q26, Q27):* `requirements_present`, `diff_present`, and `merge_recorded` are runner `command` checks, and `code_quality` is shadowed by Jev only. The major checkpoint before `release` has `diff_present`, `tests_green` (a runner `command` check), `code_quality` (a runner evaluator with a council of 3, shadowed), an optional runner evaluator, and a human step with separation of duties; a stage check before `complete`. The `verification` stage task runs the tests locally for feedback only. The merge is the protected action, authorized only at the revision the checkpoint reviewed. See `docs/definitions/sdlc-reference.yaml`.

### Runner

Decided in the 2026-10-18 intent review (constitution amendment log): gate checks run server side, so the driver only builds.

- [x] **Isolation:** *Decided:* a separate runner service next to the engine, called only by the engine, that starts a throwaway sandbox per job. Sandboxes hold no engine or database credentials.
- [x] **Code source:** *Decided:* the driver pushes the branch and names the commit SHA in the transition request; the runner clones that SHA with a read-only token. Unpushed work is never evaluated.
- [x] **Trust level:** *Decided:* runner results are engine-verified, with the runner, image, command, and SHA as provenance.
- [x] **Model access:** *Decided:* the runner's Claude Code evaluator runs headless with the owner's subscription token (`claude setup-token`) while the owner is the only person using the deployment (one person's several UI accounts count as one). A second user needs per-user credentials or an API key first (see Next). *Confirmed feasible (spike 005):* `claude -p` in a Linux container authenticates with `CLAUDE_CODE_OAUTH_TOKEN` (never `--bare`, which cannot use it), isolated with `--restricted --safe-mode --strict-mcp-config --tools Read,Grep,Glob`, returning `structured_output` against a draft-07 schema, on Sonnet. Input from spike 006: the runner reports usage from `modelUsage` in the `-p` JSON result, not `usage`, which leaves out subagent tokens. A result with `is_error` or no `structured_output` means the job did not run (*not evaluated*). See `specs/005-claude-code-driver-feasibility/`.
- [x] **Unavailability:** *Decided:* a runner job that cannot run leaves its step not evaluated, failing closed. No automatic fallback to driver-side evaluation.
- [x] **Sandbox technology:** *Decided by spike 014 (`specs/014-local-setup/`):* one Job per runner job, named after the job ID (a repeated name returns AlreadyExists, tested), in `awf-sandbox` under Pod Security `restricted`, with default-deny egress (check pods reach DNS, the Reposilite Maven proxy, and a clone proxy allowing only `github.com`; evaluator pods also reach Anthropic's `160.79.104.0/23:443`), enforced on Docker Desktop's kind mode (tested); at most 4 jobs at once, with limits on every container (check 2 CPUs and 3 GiB plus a 512 MiB PostgreSQL sidecar, evaluator 1 CPU and 1 GiB); the repository token and the `setup-token` are static Secrets read only by the clone init container and the evaluator container, and the runner has no rights on Secrets. Job classification, token isolation, and evaluator authentication are scaffolding tasks. Earlier input: *Partly decided (spike 010, Q25):* Kubernetes Jobs on the local cluster and GKE; not Google Cloud Tasks (a dispatch queue that runs no code). *Suggested:* the runner creates one Kubernetes Job per runner job, from the image the step declares, with resource limits and a deadline, in a sandbox namespace that a network policy restricts. Settled by spikes 012 and 014. *Decided by spike 012:* the runner is Java on Spring Boot and creates Jobs with fabric8 kubernetes-client 8.x, under a namespaced Role bound only in the sandbox namespace; every supported Kubernetes version (1.35–1.37) has native sidecars. Spike 014 settles the rest. Input from spike 002 (G4): sandbox pods are never privileged; services such as a database run as native sidecars gated by a startup probe, so a service that never starts means the job did not run; egress is limited to an in-cluster Maven proxy; a check may take up to 15 minutes.
- [x] **Check environment in definitions:** *Decided (spike 003):* a `command` check declares its image inline, pinned by digest; `run` as an argument list; literal `env` (no secrets); `services`, each with a `ready` command, reached at `localhost`; and a required `timeout` covering the whole job. Running past the timeout fails the check; a job that cannot run (runner down, revision not fetchable, sandbox or service never ready) leaves it *not evaluated*. See `docs/definitions/schema.md`.

### Typed decisions (Jev in the MVP, Laya later)

- [x] **Decision trees:** *Decided (spike 010, Q28):* in the MVP. A typed decision can be a decision tree declared in the definition: `then` branches with a `when` condition on the answer (`label_in`, `min_confidence`, `max_confidence`) and either `ask` (a follow-up question) or `conclude` (a named conclusion). The first match wins. At most 3 questions on a path and 5 per tree. The engine core walks it, one question per DecisionEngine call, with one runner input packet per tree. Shadow only: every node and conclusion is recorded and shown in the quality view, and nothing changes the run. The first tree asks `code_quality_band`, then the narrow `has_defect` (spike 007's battery) when the band is `poor` or `weak` or the answer is uncertain. See `docs/definitions/schema.md` and `docs/architecture/` (AD-29).

- [x] **Runtime and serving:** *Superseded for the MVP (spike 010, Q27):* Laya is not deployed in the MVP; the DecisionEngine adapter talks to Jev only, over the same wire format. The spike 007 decision below applies when Laya returns. *Decided (spike 007):* `laya-serve` (Python, FastAPI, `laya` 0.4.12, Apache-2.0), exposing `/v1/systemone`, as a container image that runs without a Mac (CPU or GPU, for example on GKE), and natively on the owner's Mac (MPS) for local testing. The engine's DecisionEngine adapter is its only caller. Jev is reached through the same adapter and wire format at `api.typesafe.ai`. The adapter records *not evaluated* whenever the provider reports truncation (`usage.truncated`). See `specs/007-laya-jev-role-in-the-workflow/`.
- [x] **MVP use:** *Decided (spike 007):* one typed question, `code_quality_band` (a 4-band `choice` with the `code_quality` rubric bands as labels, on the requirements and the diff), in shadow mode, asked of **Jev only** (spike 010, Q27; spike 007 chose both Laya and Jev), with its inputs from a runner input packet job (spike 010, Q26), and recorded next to the council median and the human decision. It is shown in the quality view, not on the approval screen. The MVP bar is the plumbing plus an honest agreement number: in the trial on ten labelled cases, zero-shot Laya was at chance, while Jev ranked good above flawed in 4–5 of 5 pairs. Jev is used for `sample-crm` only. See `specs/007-laya-jev-role-in-the-workflow/`.
- [x] **Model checkpoint:** *For the MVP (spike 010, Q27):* Jev `jev-1.13.0`, pinned; the Laya checkpoint below applies when Laya returns. *Decided (spike 007):* `laya-multilingual` with `max_len` 8192, the only checkpoint that saw every full packet (1,315–6,666 tokens). `laya` (512) and `laya-typed-decisions` (1024) were truncated on all ten cases. None beat chance zero-shot. Jev: `jev-1.13.0`, pinned. See `specs/007-laya-jev-role-in-the-workflow/`.
- [x] **Hardware:** *Not needed in the MVP (spike 010, Q27):* Jev is hosted. *Decided (spike 007), for when Laya returns:* the M5 Pro for the MVP. Measured on MPS, p50 is 75–153 ms per question on the 1024 checkpoints and 300–530 ms on `laya-multilingual` with full packets. Jev took 300–350 ms. See `specs/007-laya-jev-role-in-the-workflow/`.
- [x] **Provider call telemetry:** *Decided (spike 008); Laya parts on hold since spike 010 (Q27) took Laya out of the MVP:* the engine records every DecisionEngine provider call (Jev and Laya) in its own table, one row per attempt, with columns named after OTel `gen_ai.*` (provider, requested and resolved model, response ID, tokens, `error.type`, duration) plus run, checkpoint attempt, step, question, label, distribution, raw confidence, truncation, and the full state packet sent. No OTel export of these calls, and none from laya-serve. Model versions are pinned in engine config, never as aliases; the resolved version is recorded per call (Jev's `model`; Laya's `routing.model` plus its `/health` revision). The Laya adapter always sends `model: multilingual` (auto-routing picked the 1024-token `english` checkpoint). Retry 429, 503, 529, other 5xx, and timeouts up to 2 times with backoff, honouring `retry-after`, within 30 s; never retry 400, 401, 403, 413, or 422; a call without an answer, or a Laya `truncated: true`, is *not evaluated*. Store `x-typesafe-request-id`; TypeSafe takes no identifiers of ours. Cost is a "list-price estimate" ($0.042 per million input tokens, output free), which matched the console. The engine has its own TypeSafe key. A deployment-config allow-list of subject keys per external provider enforces "`sample-crm` only" for Jev, failing closed. See `specs/008-jev-telemetry/`.

### Observability and quality monitoring

- [x] **Telemetry pipeline:** *Decided (spike 006):* Claude Code exports events, metrics, and beta traces over OTLP/HTTP JSON, with `OTEL_LOG_TOOL_DETAILS`, `OTEL_LOG_USER_PROMPTS`, and `OTEL_LOG_ASSISTANT_RESPONSES` on. A kit setup step writes this `env` block into `~/.claude/settings.json`, because a plugin and project settings cannot enable telemetry. The driver token goes in a static `Authorization` header. An OpenTelemetry Collector deletes `tool_input`, `tool_parameters`, `error`, and `full_command`, redacts secret patterns in prompt and response text, and forwards to the engine (which checks the token on each batch and drops unbound sessions) and to a raw file while developing. No Grafana-style stack. Runner evaluators do not export OTel; the runner returns `modelUsage` from `claude -p --output-format json` in the job result. Cost is shown as an "API-equivalent estimate" next to tokens; plan-limit usage is not recorded. Still to check when the kit is built: the engine's MCP tool names in telemetry with `OTEL_LOG_TOOL_DETAILS` on (plugin servers register as `plugin:<plugin>:<server>`). See `specs/006-claude-code-telemetry/`. Calls to DecisionEngine providers are not part of this pipeline; the engine records them itself (Provider call telemetry, spike 008).
- [x] **Session correlation:** *Decided (spike 006):* the hook's `session_id` and the OTel `session.id` on every signal are the same value (confirmed by capture). One run has many sessions; a session belongs to exactly one run. The SessionStart hook binds each new session ID (startup, `/clear`, fork); a resumed session keeps its run. Starting a second run in a bound session is refused with a request to `/clear`. The engine joins telemetry on `session.id` within each binding window; laptop telemetry carries no run ID, because resource attributes are fixed at startup. Input from spike 005: `session_id` stays the same on resume and compaction and changes on fork (`source: fork`), so a forked session registers again; a session started before the driver token was set stays unregistered unless the kit registers lazily on its first PreToolUse call. See `specs/006-claude-code-telemetry/`.
- [ ] **Quality view:** *Partly decided (spike 010, Q23, Q28):* served by a separate analysis service with its own derived store, fed by engine events through a message broker (outbox on the engine side), including typed-decision tree paths and conclusions; the broker product is chosen in spikes 012 and 014. *Suggested:* one UI page with score trends per step and per definition version, failure and rework rates, tokens and duration per run, and evaluator agreement. Input from spike 003: show each test-bed task's hidden acceptance tests as an outcome measured after the run, next to its score, never part of it. Input from spike 006: what comes from telemetry, from telemetry joined with stage windows, from engine events only, and what is unavailable is agreed in `specs/006-claude-code-telemetry/` (Findings G7). Show API retries and checkpoint retries (failed attempts and rework) as separate measures. Human prompts cannot be counted cleanly, because subagent hand-backs also produce `user_prompt` events. Input from spec 005: headless runs show no plan percentages; Phase 4 logs the status line's `rate_limits` before and after each task. In the pilot, driver sessions cost $0.50–2.72 per task and an evaluator run $0.003–0.030 (list-price estimates). Input from spike 008: per question, provider, and resolved model version, with run counts, lead with within-one-band and exact-band agreement, then raw confidence (no calibration claim for either provider), latency, tokens and the Jev cost estimate, the *not evaluated* rate by cause, retries, and markers where the resolved version changes.

### Feedback loop

- [ ] **Detection:** *Partly decided (spike 010, Q23):* computed by the analysis service. *Suggested:* a simple rule over the last N runs, such as the step with the lowest average normalized score or the highest failure rate. Input from spike 002: the v1 reference workflow has no planted weakness; if no weakest step stands out after the first seed runs, consider planting one before the v2 cycle.
- [ ] **Proposal flow:** *Suggested:* a skill or slash command in the driver kit that fetches the analysis from the analysis service (spike 010), has Claude draft a YAML diff with a hypothesis, and submits it as a draft version.
- [ ] **Approval:** *Suggested:* a UI page showing the diff and hypothesis, with approve and reject; approval publishes the new version.
- [ ] **Comparison:** *Suggested:* the quality view splits metrics by definition version. With few runs, show raw numbers and run counts rather than claim significance.
- [x] **Seed data:** *Decided (spike 002):* tasks T01–T12 in `sample-crm`, run in order, with T01, T03, T04, T05, T06, and T11 rerun on the new definition version. See `docs/test-bed/README.md`. The baseline exists: tag `baseline` at `0916dbf` (spec 004). Still to do: draft each task's record (expected result and hidden acceptance tests) in a session separate from any run, for the user to review, kept in `docs/`.
- [ ] **Test-bed fixes from spike 005:** the pilot (see `specs/005-claude-code-driver-feasibility/`, Follow-ups) found that (1) the planting is visible in `sample-crm`'s git history (T05 found T02's defect through commit `41883f6`), so consider squashing the history before `baseline`; (2) T06's trap does not fire with Opus 5.5, which normalizes case and whitespace unprompted, so make it subtler or pick another task for the failed checkpoint; (3) T05's vague task led the driver to fix T02's defect and edit `sample-crm/specs/`, so decide whether runs may touch `sample-crm`'s spec kit and whether T05 reruns must start after T02. For the spec 004 work.

### Local setup

- [x] **Infrastructure:** *Decided (user, 2026-10-18):* server-side components run on Kubernetes: a local cluster for development and the MVP, and GCP (GKE) later. The same manifests should serve both.
- [x] **Local cluster:** *Decided by spike 014 (`specs/014-local-setup/`, draft in `docs/local-setup/`):* Docker Desktop 4.97.0 or later (VM 16 GB, 8 CPUs) with its Kubernetes in kind mode, one node, Kubernetes 1.36 (1.37 does not start in kind mode yet); Kustomize base with local and GKE overlays; namespaces `awf`, `awf-runner`, `awf-sandbox`; one localhost origin through Envoy Gateway (`/`, `/mcp`, `/v1`, `/analysis`) plus the collector on 4318; service images from Dockerfiles, check images on GHCR by digest; an idempotent bootstrap Job; a reset deletes only the database PVC; engine and UI run from the terminal while developing; the kit installs at local scope in the `sample-crm` checkout. Earlier: *Suggested:* a local Kubernetes cluster running the engine, MCP gateway, analysis service, UI, database, broker, runner, and OTel Collector (spike 010). No Laya service in the MVP (spike 010, Q27). Settled by spike 014.

## Spikes

Every Phase 0 spike is done. Each one's questions, log, findings, and recommendation are in its spec, and the decisions it made are recorded in the MVP items above. Spikes are named by their spec ID everywhere.

| Spec | Spike | Outcome |
| --- | --- | --- |
| [001](specs/001-concepts-what-a-workflow-is/) | Concepts: what a workflow is | Glossary, domain model, and lifecycles in `docs/concepts/` |
| [002](specs/002-test-bed-target-repository-and-task-set/) | Test bed: target repository and task set | The `sample-crm` repository and task set in `docs/test-bed/`; baseline built by feature spec 004 |
| [003](specs/003-definition-model-draft/) | Definition model draft | Definition schema, reference workflow, and example run in `docs/definitions/` |
| [005](specs/005-claude-code-driver-feasibility/) | Claude Code driver feasibility | Driver design confirmed with a prototype plugin, stub engine, and evaluator |
| [006](specs/006-claude-code-telemetry/) | Claude Code telemetry | What the OpenTelemetry export contains and how sessions correlate |
| [007](specs/007-laya-jev-role-in-the-workflow/) | Laya/Jev role in the workflow | Jev is the MVP's only typed decision provider; Laya waits until it can be trained |
| [008](specs/008-jev-telemetry/) | Jev telemetry | What the engine can observe and record about each Jev call |
| [010](specs/010-target-architecture/) | Target architecture | Components, modules, sequences, and decisions AD-1 to AD-29 in `docs/architecture/` |
| [011](specs/011-driver-api-contract/) | Driver API contract | The driver contract over HTTP and MCP, with a full T01 run, in `docs/driver-api/` |
| [012](specs/012-technology-stack/) | Technology stack | The stack in `README.md`, with hello-world trials in its `prototype/` |
| [013](specs/013-engine-low-level-design/) | Engine low-level design | Modules, ports, domain model, and draft schema in `docs/engine/` |
| [014](specs/014-local-setup/) | Local setup | Local cluster design in `docs/local-setup/` and draft manifests in `deploy/` |

To add a new spike, add an entry here with its goal questions, what it depends on, and the MVP items it settles, then run `/sdd-spike` with its topic.

## Carried-over checks

Every spike's Follow-ups section lists what it handed on. This is the one list of what is still open from them, as of 2026-10-27, so nobody has to read the twelve sections again. Each item names the spike it came from and where it is settled. Tick an item here when its check is done; the spike files stay as they are.

### Before the runner and driver kit are built

These can change the design if they fail, so run them first, as the opening tasks of the runner stub and driver kit specs.

- [ ] **Session ID injection (spike 011, Q3):** a PreToolUse hook's `updatedInput` can add `session_id` to an engine MCP tool's arguments. If not, bind every registered session of the same user on the subject and update `docs/driver-api/`. In PLAN Phase 0, driver kit skeleton.
- [ ] **Evaluator authentication behind the egress policy (spike 014):** `claude -p` with `CLAUDE_CODE_OAUTH_TOKEN` and `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1` authenticates behind the `ipBlock` policy that allows only Anthropic's API range, without `platform.claude.com`. In PLAN Phase 0, runner stub.
- [ ] **Clone token isolation (spike 014):** after an init container clones with the token from a `secretKeyRef` and `git -c http.extraHeader`, the main container finds nothing in its env, `/proc`, `.git/config`, or through the API. In PLAN Phase 0, runner stub.
- [ ] **Job classification (spike 010 Q13, spike 014):** with fabric8 8.x, Jobs that pull a missing image, request 64 CPUs, OOM the main container, leave a sidecar never ready, exit 1, exceed `activeDeadlineSeconds`, and get evicted; record the pod and container state the runner reads, so it reports *could not run* and *failed* apart (spike 003 Q32). Also that fabric8 surfaces a repeated Job name as HTTP 409 (plain `kubectl` did: `specs/014-local-setup/prototype/RESULTS.md`). In PLAN Phase 0, runner stub.
- [ ] **Merge commits on `sample-crm` (spike 010, Q26):** the repository allows merge commits, since `merge_recorded` checks that the target branch contains the reviewed revision. Five minutes on GitHub. In PLAN Phase 0, manifests.

### Local setup and manifests

Plumbing from spike 014 that its draft assumes. In PLAN Phase 0, Kubernetes manifests, unless noted.

- [ ] **Envoy Gateway on localhost:** installs from its release `install.yaml` without Helm, and `desktop-cloud-provider-kind` publishes its LoadBalancer on localhost.
- [ ] **Native engine routing:** `/v1` and the MCP gateway route to `host.docker.internal` while the engine runs from the terminal (`docs/local-setup/README.md`, Development loop).
- [ ] **Clone proxy product:** a forward proxy that allows only `github.com`.
- [ ] **Local images without a push:** kind mode runs images built into Docker Desktop's containerd store.
- [ ] **Tooling:** a `kubectl` matching the 1.36 cluster (the owner's is 1.34.1); pre-pull images, since first pulls through the VM network can take over an hour.
- [ ] **One origin (spike 014, not run):** UI, engine, gateway, and analysis behind one localhost ingress; log in, confirm the cookie reaches `/v1`, and connect Claude Code to `http://localhost/mcp` without a validate warning.
- [ ] **Emulator restart (spike 012, spike 014, not run):** topics and subscriptions are recreated idempotently on every Pub/Sub emulator start, and the engine and analysis recover after a restart.
- [ ] **Check image by digest (spike 014, not run):** build and push the `sample-crm` check image, run a Job pinned to `image@sha256:…`, and set its digest and the PostgreSQL image digest in `docs/definitions/sdlc-reference.yaml`.
- [ ] **Sidecar check timing (spike 002 G4, spike 014, not run):** a `postgres:18-alpine` native sidecar with a startup probe next to `eclipse-temurin:25-jdk` running `./mvnw -B verify`, cold and warm, through the Maven proxy; the wrapper download goes through the proxy too. Gives the 15-minute budget its first real number.

### Collector

In PLAN Phase 0, OTel Collector.

- [ ] **Redaction (spike 006):** an `attributes` processor deletes `tool_input`, `tool_parameters`, `error`, and `full_command`, and secret patterns are redacted in `prompt`, `prompt_text`, `response`, and the `user_prompt` span attribute; agent, skill, and MCP names survive.
- [ ] **Engine tool names in telemetry (spike 006):** with `OTEL_LOG_TOOL_DETAILS` on, confirm how the engine's MCP tools appear (plugin servers register as `plugin:<plugin>:<server>`).
- [ ] **`/clear` (spikes 005 and 006, never tried):** a `/clear` fires SessionStart with `source: clear`, and the new session ID shows up in telemetry. Ten minutes, interactive.

### Driver kit

Checked when the kit is built (spikes 005 and 011). In PLAN Phase 1, Claude Code driver kit.

- [ ] **MCP result visibility:** what the model sees of a tool result with `structuredContent` plus `outputSchema` against text only, and whether the `outputSchema` validation bug appears.
- [ ] **Stop hook while a subagent runs:** use `background_tasks` so an early stop is not counted while a subagent is still working (spike 006 saw the Stop hook fire and the hand-back arrive as a `user_prompt`).
- [ ] **Status size:** the reference workflow's `status` at an open `approve_release` attempt, with the kit instructions, stays under the 10,000-character cap.
- [ ] **Plugin options reach hooks:** with a marketplace install at local scope, `userConfig` options with a default are exported to hook processes (with `--plugin-dir` they were not, spike 005 G6).

### Engine and providers

- [ ] **Denial idempotency (spike 013):** deleting a denial's idempotency row before commit lets a waiting retry run again, as the tested rollback did. In PLAN Phase 1, driver API.
- [ ] **Jev error shapes (spike 008):** the real 429 and 529 bodies and whether they carry `retry-after`; the adapter uses its own backoff until then. Only if it matters at more than shadow-mode volume, and the burst only with the user's go-ahead.

### Test bed and Phase 4

- [ ] **Task records (spike 002, Q10):** draft each task's expected result and hidden acceptance tests under `docs/`, in a session separate from any run, for the user's review. See Seed data. Not in PLAN yet; needed before Phase 4.
- [ ] **Test-bed fixes (spike 005):** the three undecided fixes under "Test-bed fixes from spike 005" above. Needed before Phase 1's exit run on a real repository.
- [ ] **Plan usage (spike 005, G7):** a status-line script logs `rate_limits` before and after each Phase 4 task, since headless runs show no plan percentages.
- [ ] **Hidden tests as an outcome (spike 003, Q21):** measure each task's hidden acceptance tests after the run, outside the score, and show them next to it in the quality view.
- [ ] **Weakest step (spike 002, Q8):** if no weakest step stands out after the first seed runs, plant one before the v2 cycle. See Detection.

### Closed without action

Listed once so they are not re-read as open: Kubedock, Docker-in-Docker, and gVisor comparisons (spike 002 G4; sandbox technology settled by spike 014); offline `./mvnw -o verify` and the `~/.m2` size (replaced by the in-cluster Maven proxy); OTel export from a headless evaluator in a container (runner evaluators do not export OTel, spike 006); a `/goal` turn limit (the kit uses a Stop hook, spike 005 Q7); the validator slice, the mini replay, and the paper replay of the example run (Phase 1 tests, spike 013); the stateless MCP SDK trial, the evaluator token proxy, the Laya adapter conventions, and the article toy workflow ("Next" items below).

## Next: improvements after the MVP

- [ ] **Stateless MCP gateway:** move the gateway to a stateless MCP SDK (Java SDK 2.2.x `modern` server, protocol `2026-07-28`) once it is GA and a trial shows Claude Code connects without claude-code#39790, so the gateway can run more than one replica (spike 012, Q8).
- [ ] **Laya as a second provider:** deploy `laya-serve` again behind the DecisionEngine port once fine-tuning makes it useful (spike 010, Q27; spike 007 has the serving decisions).
- [ ] **Laya fine-tuning:** fine-tune Laya on labelled code-review cases from real runs, and fit its temperatures once enough labels exist. It is the only route to a useful Laya code-review signal; zero-shot it was at chance (spike 007).
- [ ] **Review the typed questions asked of Jev:** settle the questions and their wording before relying on Jev's shadow data, starting from spike 007's question battery (`specs/007-laya-jev-role-in-the-workflow/prototype/report2.html`): a narrow `has_defect` check separated good and flawed code as well as the Claude council, while a band question and a median over several questions did not.
- [ ] **Human step assist with risk flags:** narrow `noul` risk checks (start from "SQL is built from request input", which Jev answered 0.06 against 0.98 on T11) shown to the approver at `release_approval`, informing but never deciding. The first later role, before run categorization and transition guidance (spike 007, Q8).
- [ ] **Laya beyond shadow mode:** advisory gate assist on the approval screen, transition recommendations, and promotion based on evidence.
- [ ] **Human step scoring:** the approver picks a rubric band in the UI.
- [ ] **Definition authoring** in the UI, beyond feedback-loop proposals.
- [ ] **System-attested evidence** from one CI or git hosting integration.
- [ ] **Runner-produced evidence:** runner checks produce engine-verified evidence (such as a test summary at the SHA) that built-in checks can read, with a `min_trust` key on step inputs or a source on each evidence type so a driver cannot supply it (spike 003, Q23, Q24).
- [ ] **Real identity:** identity provider, roles, driver token expiry and revocation.
- [ ] **Operational settings in the database:** runner and DecisionEngine provider settings stored by the engine and edited in the UI, instead of deployment configuration that needs a redeploy (spike 010, Q21).
- [ ] **Evaluator token proxy:** a runner-side proxy adds the subscription token, so the `setup-token` never enters an evaluator sandbox (spike 010, Q10; untested).
- [ ] **Notifications outside the UI:** email or chat when a human step or an escalation is waiting (spike 010, Q19).
- [ ] **Full runner logs:** keep complete job logs, not only the ~64 KB tail in the job result (spike 010, Q18).
- [ ] **Typed-decision trees beyond shadow:** let a tree's conclusion inform the approver (`advisory`) or act on the run (`bounded_control`), once measured evidence supports it (spike 010, Q28).
- [ ] **Multi-user robustness:** driver leases with heartbeat and takeover.
- [ ] **Observed driver capabilities:** compare a session's declared capabilities with what it does, for example `enforcement` declared but no gate calls, and flag or refuse the mismatch. The MVP only records declarations and checks them at bind (spike 011, Q9).
- [ ] **Evaluation tasks and runner jobs:** retries when an evaluator never submits or a runner job hangs. The MVP bound is decided (spike 010, Q6): a runner default timeout for evaluator jobs plus an engine deadline that records *not evaluated*.
- [ ] **Runner performs protected actions:** the engine has the runner do the merge with write credentials the laptop never holds, making protection authoritative instead of hook-based.
- [ ] **GKE Sandbox for sandbox pods:** run runner sandboxes under the gVisor RuntimeClass on GKE, with limits on every container and the metadata server blocked (spike 010, Q25).
- [ ] **Cloud Run Jobs runner adapter:** an optional second sandbox backend behind the runner interface, if GKE Jobs fall short (spike 010, Q25).
- [ ] **GCP deployment:** run the same manifests on GKE, and choose the GCP pieces: cluster mode (Standard or Autopilot), managed database, secrets, and ingress.
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
- [ ] DecisionEngine providers beyond Jev and Laya (Jev is the only shadow provider in the MVP, spike 010).
- [ ] A second driver provider after Claude Code.
- [ ] Parallel stages (fork and join) and sub-workflows in the definition model (spike 001, Q2).
