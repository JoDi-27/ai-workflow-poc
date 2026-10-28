---
id: 010
title: "Target architecture"
type: spike
status: done
phase: 0
depends_on: [001, 003, 005, 006, 007]
refs: ["PLAN Phase 0 / Spikes / 7. Target architecture", "BACKLOG Spikes / Wave 3 / 7. Target architecture", "BACKLOG MVP / State and events", "BACKLOG MVP / Auth", "BACKLOG MVP / Result delivery to the driver", "BACKLOG MVP / Runs and repositories", "BACKLOG MVP / Evidence"]
updated: 2026-10-21
---

# 010 · Target architecture

<!--
A spike answers questions and settles decisions; it does not ship production code. Run it with /sdd-spike 010:
the spike-researcher agent prepares a briefing and questions, you answer and add information over as many sessions
as it takes, and everything learned is recorded here. Prototypes are throwaway and live under specs/010-*/prototype/.
Progress counts the checkboxes in Goal questions, Questions for you, and Deliverables.
-->

## Inbox

<!-- Drop notes, links, constraints, or decisions here at any time, in any form. The next /sdd-spike session moves them into the Log and Findings and clears this section. -->

## Why

<!-- The decision this spike unblocks, and the BACKLOG.md MVP items it settles. -->

Waves 1 and 2 settled the domain model (001), the definition model (003), the Claude Code driver and runner evaluator (005), Claude Code telemetry (006), and the role of Laya and Jev (007). Nothing yet puts them together into one system: which components exist, where their boundaries are, how each happy path step flows through them, what is persisted, and who can reach what.

This spike combines those results into the system's shape, marking clearly what the MVP builds and what is only the target. It unblocks spike 8 (driver API contract), spike 9 (technology stack), and the Phase 0 scaffolding. It settles the State and events, Auth, Result delivery to the driver, Runs and repositories, and Evidence items in `BACKLOG.md`.

Spike 008 (Jev telemetry) feeds this spike but is not a dependency. It runs in parallel in another session; where its result affects telemetry ingestion or the DecisionEngine adapter, this spike leaves a placeholder and takes the result in once 008 closes.

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [x] **G1** Components and their boundaries: engine modules (definitions, runs and state, checkpoint execution, scoring, evaluation tasks, runner dispatch, DecisionEngine adapters, telemetry ingestion, driver API, UI API), UI, runner (with its source and evaluator adapters), Laya service, collector, database, and driver kit.
- [x] **G2** The runner interface: the job packet, how results come back, timeouts, and how the engine tells a runner job that failed to run from a check that failed.
- [x] **G3** Sequences for each happy path step: start and bind, submit evidence, request a transition, run a checkpoint with runner checks, runner-side reasoning, human, and Laya steps, authorize a protected action, correlate telemetry, and propose and publish a definition version.
- [x] **G4** How long-running checkpoints progress: what moves a checkpoint forward while it waits hours for a human, and how the driver and UI learn about it.
- [x] **G5** Persistence: state tables, the event table, what can be replayed, and how requests on the same run are serialized and made idempotent.
- [x] **G6** Trust boundaries and auth: what the agent on the laptop can reach, how driver tokens and UI sessions are kept apart, what the runner and its sandboxes can reach and which credentials they hold (repository read token, subscription token), and which risks the MVP accepts.
- [x] **G7** Where provider-neutrality and SDLC-neutrality are enforced in the module structure.

## Briefing

<!-- Prepared by the spike-researcher agent: what the repository and current docs already say, with sources. -->

Prepared 2026-10-19 by spike-researcher. INTENT = `docs/intent.md`. Spike 008's Inbox findings are raw and marked "008 (unsettled)".

### G1 · Components and boundaries

- Known: server-side components are the engine, the UI (deployed separately), `laya-serve`, and the runner. The driver (agent plus kit) runs on the laptop. Everything server-side runs on Kubernetes. (INTENT Runtimes and deployables; BACKLOG Local setup)
- Known: the INTENT Responsibilities table assigns ownership. The engine owns persistence, replay, idempotency, and leases. The UI holds no authoritative state. The runner holds no engine credentials and no state.
- Known: the driver API uses the MCP Java SDK (2.1.0 tried) with the stateful Streamable HTTP servlet transport and a static bearer token. Hooks are command hooks that call the engine's HTTP API through one script. (spec 005 Recommendation)
- Known: an OTel Collector sits between the laptop and the engine. It deletes four attributes, redacts secrets, and forwards to the engine and a raw file. The engine checks the driver token on each batch. (spec 006)
- Known: one DecisionEngine adapter calls `laya-serve` and Jev (`api.typesafe.ai`), both over `/v1/systemone`. Only the engine calls them. (spec 007)
- Known: the runner has source adapters (git) and evaluator adapters (headless Claude Code), with one throwaway sandbox per job.
- Suggested: one relational database, with small evidence payloads and telemetry stored in it and no object storage. (BACKLOG Database) Conflict: INTENT Persistence wants content-addressed storage. Treat it as target only.
- Suggested: one Kubernetes Job per runner job. `laya-serve` may run natively on the Mac. (BACKLOG Runner, Local cluster)
- Open: whether the engine is one deployable or several, where durable timers and deadlines live, whether the UI API and the driver API share a process, and how adapters are kept apart from the core.

### G2 · Runner interface

- Known: a `command` job carries an image by digest, `run` as an argv, a literal `env`, `services` with `ready` commands, a whole-job `timeout`, and the revision. (`docs/definitions/schema.md` Steps) An evaluator job carries the task ID, the step, the rendered rubric, the revision, and evidence references. (INTENT Fresh-context evaluation)
- Known outcomes:
  - Exit 0 passes. A non-zero exit, or running past `timeout`, fails.
  - These are *not evaluated*: runner unavailable, revision not fetchable, sandbox never starts, service never ready.
  - For an evaluator, `is_error` or a missing `structured_output` is *not evaluated*.

  (schema.md; spec 003 Q32; spec 005)
- Known: the result carries provenance (runner, image, command, revision), the log, and evaluator usage from `modelUsage`. In the MVP, runner checks produce no evidence. (spec 003 Q23; spec 006 Q7)
- Known: services run as native sidecars gated by a startup probe, pods are unprivileged, and the only egress is the in-cluster Maven proxy. Each check gets 15 minutes at most. (spec 002 G4; `docs/test-bed/README.md`)
- Known: evaluation task states are `issued → submitted | failed_to_run | cancelled`. Cancelling an attempt cancels its jobs, and late results are rejected. (`docs/concepts/model.md`) Timeouts and retries for hung jobs are BACKLOG "Next".
- Gap: `agent_evaluator` steps have no `timeout`. Conflict: model.md has `RUNNER_JOB produces EVIDENCE`, but spec 003 Q23 says none in the MVP. Treat it as target only.
- Open: push or pull of results, a council with a member that cannot run (spec 003 follow-up), job ID and dispatch idempotency, where logs live, and how OOM, eviction, and similar failures are classified.

### G3 · Sequences per happy path step

- Known, start and bind:
  - SessionStart registers the session, unbound if there is no run.
  - `start_run` is an explicit MCP call, and MCP tools take the subject as an argument.
  - Forks and `/clear` bind again.
  - An unknown session is registered lazily on its first PreToolUse.
  - A second run in a bound session is refused.

  (spec 005 G2; spec 006 Q2, Q11)
- Known, transition:
  - The request carries the revision and an idempotency key.
  - A run has at most one open attempt.
  - Steps run strictly in order and fail fast.
  - Each attempt re-runs every step.

  (INTENT Transition protocol; spec 001 Q5, Q7, Q17)
- Known, human steps: they are queued in the UI inbox when the attempt reaches them. Separation of duties applies to approvals and overrides, and a rejection cannot be overridden. (spec 003 Q2, Q13, Q18)
- Known, Laya and Jev: shadow `typed_decisions` run when `code_quality` runs and never delay or block. Truncation or a provider failure makes only that decision *not evaluated*. (schema.md; spec 007)
- Known, protected action: a PreToolUse hook on every tool calls the engine, and merges go only through `gh pr merge`. `authorize_action` requires the stage to be in `allowed_in` and the revision to equal the reviewed revision. (spec 005 G3; schema.md)
- Known, telemetry: laptop → collector → engine. The engine joins on `session.id` within binding windows and drops unbound sessions. (spec 006)
- Suggested, feedback loop: a kit skill drafts a YAML diff and submits it as a draft, and approving it in the UI publishes it. (BACKLOG Feedback loop)
- Open:
  - How the engine moves on after each asynchronous result.
  - Whether the shadow calls run in parallel with the council.
  - Which credential submits a draft definition.
  - The exhaustion escalation and "owner directs a rework" sequences.

### G4 · Long-running checkpoints

- Known: a checkpoint waiting on a human moves forward when the engine records the decision. The driver and the owner learn about it by pulling `status` or `checkpoint_status`. (BACKLOG spike 7 input from article 9; constitution Art. 9) The engine may react to the result or deadline of work it dispatched itself. (Art. 9)
- Known: in spec 005 G9 (trials 13–15), a generic session stopped at the human gate, and a fresh session prompted with "Continue." resumed from the injected status.
- Known: exhaustion always escalates to the owner in the UI, who can extend, direct a rework, or abandon. (spec 001 Q24)
- Suggested: the UI and the driver poll. (BACKLOG UI technology, Result delivery to the driver) Assumption (spec 001): "directs a rework" means the owner tells the driver to take the rework transition.
- Open: whether a driver session waits or ends at a human gate, what bounds an attempt whose job never returns, and whether humans get notifications outside the UI.

### G5 · Persistence, replay, serialization, idempotency

- Known: state changes and their events are written in one transaction, and *not evaluated*, *failed*, and *passed* stay distinct. (Art. 7) INTENT names at least 27 event types, each with actor, actor type, timestamp, run, definition version, correlation ID, session, evidence references, and reason. Replays must rebuild the same state and score. (INTENT milestone 2)
- Known: requests on one run are serialized, idempotency keys prevent advancing twice, and a run has at most one active lease. Lease heartbeat and takeover are "Next". (INTENT; BACKLOG)
- Suggested: state tables plus an append-only event table. (BACKLOG State and events) Conflict: INTENT wants hash-chained events, and BACKLOG "On record" deprioritizes that. Treat it as target only.
- Open: the source of truth, what replay covers, which operations take idempotency keys, the locking scheme, and whether telemetry counts as run state.

### G6 · Trust boundaries and auth

- Known: human decisions and definition approvals are accepted only from human-authenticated UI sessions, and driver tokens never carry approval scopes. (Art. 2)
- Known: the driver token is a sensitive plugin `userConfig` value, sent as a bearer token on MCP, hooks, and OTLP. A wrong token gets 401. (spec 005, spec 006)
- Known: sandboxes hold no engine or database credentials. Evaluators use `CLAUDE_CODE_OAUTH_TOKEN` (the owner's `setup-token`) with isolation flags. Sandbox egress is the Maven proxy only, with no route to the collector. (spec 002, spec 005)
- Known: only the engine holds the Jev key, the engine needs egress to `api.typesafe.ai`, and Jev sees `sample-crm` code only. (spec 007 Q5)
- Known, accepted risks:
  - One person holds both UI accounts.
  - Merge protection is hook-based, and GitHub branch protection is the real gate.
  - The laptop is trusted (Art. 8).
- Known: `sample-crm` is public. Suggested: one static driver token per user, and a simple UI login. (BACKLOG Auth)
- Open: how the UI proves a human to the engine, whether the evaluator sandbox may hold the subscription token, whether a repository token is needed, driver token scopes in the feedback loop, and which endpoints the laptop can reach.

### G7 · Where neutrality is enforced

- Known: `docs/concepts/model.md` (What the engine knows versus what definitions declare) sets the split. Git and Claude Code specifics belong in runner adapters and driver kits. (INTENT Design guardrails) A non-SDLC toy workflow exists on paper, and writing it in the schema is "Next".
- Leak: in spec 005 the engine reads the full command to detect `gh pr merge` and to deny `git push`. Those are git and Claude Code concepts in the engine.
- Leak: telemetry ingestion reads Claude Code names (`claude_code.*`, `session.id`, `query_source`). (spec 006)
- Known: provider names (`laya`, `jev`) appear in definitions by design. (schema.md rule 12)
- Open: how neutrality is enforced (module rules, a toy-workflow test, or both), and where the telemetry and protected-action mappings live.

### Researched

- MCP 2025-11-25 Streamable HTTP: the server may assign `MCP-Session-Id` at initialize and may end a session at any time with 404, after which the client re-initializes. A GET SSE stream is optional. ([transports 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports))
- MCP 2026-07-28: no initialize handshake and no protocol sessions. Metadata travels in `_meta`, servers do not initiate requests, and capabilities come from `server/discover`. A Tasks extension returns a `taskId` that the client polls with `tasks/get`, a possible later fit for `checkpoint_status`. ([transports 2026-07-28](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports); [AAIF migration note](https://aaif.io/blog/mcp-2026-07-28-whats-changing-and-how-to-migrate))
  - Inference: keep no run state in MCP sessions. Spec 005's subject-as-argument and a pull-only `status` already fit.
- MCP Java SDK: 2.1.0 is the latest stable and tracks 2025-11-25. 2.2.0-M1 has a stateless 2026-07-28 server. Spec 005 saw Claude Code try `2026-07-28` first and fall back. ([releases](https://github.com/modelcontextprotocol/java-sdk/releases))
- Kubernetes Jobs: `activeDeadlineSeconds` overrides `backoffLimit`. On expiry the Job becomes `Failed` with `DeadlineExceeded`, and since 1.31 terminal conditions are set only after the pods terminate. `ttlSecondsAfterFinished` deletes the pods, so the runner must collect results first. ([Jobs](https://kubernetes.io/docs/concepts/workloads/controllers/job/))
  - Inference: a Job-level deadline cannot tell "never started" from "ran too long", so the runner must read pod and container state.
- `podFailurePolicy` (stable 1.31) acts on exit codes and pod conditions. `DisruptionTarget` reasons (preemption, taint deletion, eviction, pod GC, kubelet termination) are infrastructure causes, a natural fit for *not evaluated*. `Pending` includes scheduling and the image pull. ([Disruptions](https://kubernetes.io/docs/concepts/workloads/pods/disruptions/); [Pod lifecycle](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/))
- Termination messages are capped at 4096 bytes, so evaluator JSON comes back through logs or a result file. Native sidecars are stable since 1.33 and do not block Job completion. ([pod failure reason](https://v1-32.docs.kubernetes.io/docs/tasks/debug/debug-application/determine-reason-pod-failure/); [sidecars](https://kubernetes.io/docs/concepts/workloads/pods/sidecar-containers/))
- Idempotency-Key (IETF draft-07):
  - The same key with a different payload gets 422.
  - A retry while the first request is in flight gets 409.
  - A retry after completion gets the stored result.

  ([draft-07](https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html))
- Per-run serialization patterns: a row lock (`SELECT … FOR UPDATE`) or a transaction-level advisory lock ([PostgreSQL explicit locking](https://www.postgresql.org/docs/current/explicit-locking.html)). Dispatch can use a transactional outbox with an idempotent consumer ([microservices.io](https://microservices.io/patterns/data/transactional-outbox.html)). These are patterns only; spike 9 picks the database.
- Inference, to confirm: naming a Job after the engine's job ID makes a repeated dispatch return AlreadyExists.
- 008 (unsettled): every Jev response carries `x-typesafe-request-id`. There are no rate-limit headers. An extra body `metadata` field is rejected, so run and step IDs cannot travel in the body. The engine keeps the correlation itself, with our IDs plus the provider's request ID.

## Questions for you

<!-- Prepared by the agent, highest impact first; each says which goal questions it serves, why it matters, the options, and a suggested answer. Ticked when answered or skipped. -->

- [x] **Q1** (G1, G5, G7) Should the engine be one deployable with internal modules, or should some modules be separate services? *Why:* it decides whether state, events, and dispatch share a transaction, and whether neutrality is enforced by module rules or by network boundaries. *Options:* one deployable with modules / telemetry ingestion as its own service writing through the engine API / several services around one database. *Suggested:* one deployable. Articles 1 and 7 want one writer and one transaction, and there is one user.
- [x] **Q2** (G2, G4, G6) How do runner results get back to the engine? *Why:* the runner is "called only by the engine" and holds no engine credentials, but a callback needs a credential. *Options:* the engine polls the runner / the runner calls back with a runner-only credential (amend INTENT) / one long request held open per job. *Suggested:* the engine polls. It keeps trust one-way, the engine notices an unreachable runner and records *not evaluated*, and with 15-minute checks the polling delay does not matter.
- [x] **Q3** (G7, G3, G6) Who decides that a tool call is a protected action? *Why:* today the engine parses Bash for `gh pr merge` and `git push`, which puts git and Claude Code into the engine (Art. 3). The answer shapes `authorize_action` in spike 8. *Options:* the kit hook maps a tool call to an action name and revision, and the engine checks only action, stage, and revision / the definition declares match patterns that the engine applies / keep the parsing in the engine. *Suggested:* the kit maps it. GitHub branch protection stays the real gate.
- [x] **Q4** (G6) How does the engine know a UI request comes from an authenticated human? *Options:* the engine owns human login and issues the session, and the browser calls the engine's UI API / the UI backend logs users in and asserts the user with a service credential / an external identity provider ("Next"). *Suggested:* the engine owns login, so no service credential can ever approve.
- [x] **Q5** (G5) Are the state tables or the events the source of truth? *Options:* state tables are the truth, events are written in the same transaction, and replay is a check that rebuilds state and score and compares / full event sourcing / state only, with an audit log that is never replayed. *Suggested:* state tables plus events, with replay as a check.
- [x] **Q6** (G2, G4) What bounds a runner job or evaluation that never returns? *Why:* without a bound the attempt stays open forever, and only abandoning the run frees it. Only `command` checks have a `timeout`. *Options:* a runner-wide default for evaluator jobs plus an engine deadline (job timeout plus a margin) that records *not evaluated* / add `timeout` to `agent_evaluator` / no bound, and the owner abandons the run. *Suggested:* the runner default plus the engine deadline. It fails closed with no schema change.
- [x] **Q7** (G4, G3) At a human gate, does the driver session end and resume later from `status`, or keep polling `checkpoint_status`? *Options:* end and resume (proven in spec 005 G9) / poll with back-off / end, and the kit offers `/loop` re-checks. *Suggested:* end and resume, which costs nothing while waiting.
- [x] **Q8** (G3, G4) When the owner resolves an exhaustion escalation with "rework", does the engine take the rework transition itself or record a directive the driver acts on? *Options:* the engine commits it, since the edge has no checkpoint / the engine records a directive in `status` and the driver requests the transition / it depends on whether the edge has a checkpoint. *Suggested:* the engine commits it. Art. 9 allows changes in response to a human UI decision.
- [x] **Q9** (G7, G1) Where are Claude Code telemetry names mapped to neutral measures? *Options:* in the kit-owned collector config, renamed before forwarding / in a per-provider ingestion adapter module in the engine (amend the guardrail) / store raw and map at query time. *Suggested:* the kit-owned collector config, with an engine adapter as the fallback.
- [x] **Q10** (G6) Do you accept, as an MVP risk, that the evaluator sandbox holds your `setup-token` and has egress to Anthropic's API while it reads agent-written code? *Options:* accept, with egress only to Anthropic's hosts and the spec 005 isolation flags / a runner-side proxy so the token never enters the sandbox (unverified) / evaluate a packet built outside the sandbox. *Suggested:* accept and record it. The tools are read-only, the repository is yours and public, and the proxy becomes a "Next" item.
- [x] **Q11** (G6, G2) Since `sample-crm` is public, does the MVP runner clone it anonymously, with no repository token? *Options:* anonymous now, token later / a fine-grained read-only token now / a GitHub App now. *Suggested:* anonymous, one secret fewer.
- [x] **Q12** (G2) If one council member's runner job cannot run, what happens to the step? *Options:* the whole step is *not evaluated* / the median of the members that ran, if a majority ran / retry the member once, then *not evaluated*. *Suggested:* the whole step is *not evaluated*, the simplest fail-closed rule.
- [x] **Q13** (G2) How are unnamed infrastructure failures classified (OOMKilled, eviction or preemption, image pull failure)? *Why:* *failed* costs 5 points and counts in failure rates, while *not evaluated* costs nothing. *Suggested:* `DisruptionTarget`, image pull, unschedulable, and clone failures are *not evaluated*. OOMKilled during the main command is *failed*, with the reason shown, because the subject's code exceeded the published limits.
- [x] **Q14** (G5, G6) Does the MVP implement driver leases, and when is a lease acquired and released? *Why:* separation of duties excludes anyone who ever held a lease on the run. *Options:* binding a session acquires it for its user, and another user is refused unless the owner takes over in the UI / no leases, and separation of duties uses the users of every bound session / a lease per session. *Suggested:* binding acquires it, and only the owner can take over in the UI.
- [x] **Q15** (G6, G3) What may the driver token do in the feedback loop? *Options:* read analysis and submit drafts, never publish / read only, with drafts uploaded in the UI / a separate token. *Suggested:* read analysis and submit drafts. Publishing stays a UI-only human action.
- [x] **Q16** (G5) Which operations take idempotency keys, and how are requests on one run serialized? *Suggested:* every mutating driver call (`start_run`, `submit_evidence`, `request_transition`, `submit_evaluation`, draft submission) and every human UI decision carries a key. A key reused with a different payload is rejected, and a retry returns the stored result (IETF Idempotency-Key draft). Requests on one run are serialized by locking the run's row for the transaction. Runner dispatch goes through an outbox written in the same transaction.
- [x] **Q17** (G3) Do the shadow typed decisions (Laya, Jev) run in parallel with the `code_quality` council, or after it? *Suggested:* in parallel, started when the step starts, each with its own engine deadline. The checkpoint never waits for them.
- [x] **Q18** (G2) Where do runner job logs live? *Suggested:* the runner returns a bounded log tail (for example the last 64 KB) in the job result, the engine stores it with the step result as provenance, and full logs are not kept in the MVP.
- [x] **Q19** (G4) Do humans get any notification outside the UI (email, Slack) when a human step or an escalation is waiting? *Suggested:* none in the MVP. The UI inbox is the only channel, and notifications go to BACKLOG "Next".
- [x] **Q20** (G1, G6, G7) Should the MCP server be its own deployment? Raised by the user after the recommendation was agreed. *Options:* a separate, stateless MCP gateway that calls the engine's public HTTP driver API with the caller's driver token / MCP as a module inside the engine (Q1 as first drafted). *Suggested:* the separate gateway.
- [x] **Q21** (G1, G5, G6, G7) Where are configurations held, and by which engine modules? Raised by the user. *Options:* split by kind: workflow configuration and identity in the database, operational settings in deployment config, secrets in Kubernetes Secrets / everything except secrets in the database, as INTENT Persistence says. *Suggested:* split by kind.
- [x] **Q22** (G5, G1) Is the database relational or non-relational, given that definitions are YAML documents? Raised by the user, who asked whether MongoDB fits definitions and run history better. *Options:* one relational database with document columns for document-shaped data / MongoDB for everything / MongoDB for definitions and a relational database for run state. *Suggested:* one relational database with document columns.
- [x] **Q23** (G1, G3, G5, G6, G7) Should analysis (the quality view and cross-run analysis for the feedback loop) be its own deployment, and how does it get its data? Raised by the user. *Options:* a separate analysis service fed by a broker that the engine publishes events to from its outbox / a separate service that pulls a cursor feed from the engine (long-poll) / a separate service with read-only database access (amends the persistence rule) / a module in the engine. *Suggested at first:* the cursor feed. *Decided:* the broker.
- [x] **Q24** (G1, G5, G7) Should the engine have inbound ports as well as outbound ones? Raised by the user. *Options:* inbound ports as use-case interfaces (`DriverOperations`, `HumanOperations`, `TelemetryIntake`, `EventFeed`, `DueWork`), with the timer moved out of the core into a `scheduler` inbound adapter / edges keep calling core modules directly. *Suggested:* inbound ports.
- [x] **Q25** (G2, G6) Is Google Cloud Tasks feasible for the cloud sandbox? Raised by the user, who asked for research first. *Decided:* keep Kubernetes Jobs as the sandbox, make GKE Sandbox (gVisor) the target on GKE, do not use Cloud Tasks, and keep Cloud Run Jobs as a possible later runner adapter. Follow-ups from the user: Cloud Tasks may serve only as an optional delivery leg after the outbox, and Cloud Run functions are rejected as a sandbox.
- [x] **Q26** (G2, G3, G6) Since git syncs the change to the runner, what evidence must the driver still submit? Raised by the user, with follow-ups: the UI may read git, and the diff for typed decisions may come from inside the cluster. *Decided:* git is the evidence channel. The reference workflow needs no driver-submitted evidence; the transition request carries only the revision. The runner reads git for checks, evaluators, and typed-decision packets, and the browser reads it for humans.
- [x] **Q27** (G1, G3) Is Laya part of the MVP? Raised by the user. *Decided:* no. Laya needs training to be useful (zero-shot it was at chance, spike 007), which is too costly now. Jev is the only DecisionEngine provider in the MVP.
- [x] **Q28** (G1, G3, G5, G7) Where does a decision tree for Jev live (depending on an answer, ask follow-up questions or reach a conclusion), and is it in the MVP? Raised by the user. *Decided:* in the MVP. The tree is declared in the definition and walked by a core `typed-decisions` module, one question per DecisionEngine call. The tree stays in shadow mode.

## Log

<!-- Append-only, oldest first: "- YYYY-MM-DD · <source> · <what was answered, added, or found>". -->

- 2026-10-19 · research · spike-researcher briefing for G1–G7 and 15 questions for the user, recorded in Briefing and Questions for you. Found two neutrality leaks (engine parsing Bash for merges; ingestion reading `claude_code.*` names) and three doc conflicts (content-addressed storage, hash-chained events, `RUNNER_JOB produces EVIDENCE`), all treated as target only until decided.
- 2026-10-19 · answer to Q1 · One engine deployable with internal modules (definitions, runs and state, checkpoint execution, scoring, evaluation tasks, runner dispatch, DecisionEngine adapters, telemetry ingestion, driver API, UI API). No module split into its own service.
- 2026-10-19 · answer to Q2 · The engine polls the runner for job status and results. The runner never calls the engine and holds no engine credential.
- 2026-10-19 · answer to Q3 · The driver kit's hook maps a tool call to a neutral action name (for example `merge`) plus a revision. The engine checks only action, stage (`allowed_in`), and revision. This replaces spec 005's design, where the engine read the full command.
- 2026-10-19 · answer to Q4 · The engine owns human login and issues the human session. The browser calls the engine's UI API with it, and no service credential can approve anything.
- 2026-10-19 · answer to Q5 · State tables are the source of truth. Events are written in the same transaction, and replay is a check that rebuilds state and score from the events and compares them with the tables.
- 2026-10-19 · answer to Q6 · A runner-wide default timeout bounds evaluator jobs. The engine also sets a deadline per runner job (the job's timeout plus a margin) and records *not evaluated* when it passes. No schema change.
- 2026-10-19 · answer to Q7 · At a human gate the driver session ends, and a later session resumes from the status injected at session start (as in spec 005 G9). No polling inside the session.
- 2026-10-19 · answer to Q8 · When the owner resolves an exhaustion escalation with "rework", the engine commits the rework transition itself on that UI decision, and `status` shows it. This replaces spec 001's assumption that the owner tells the driver to take it.
- 2026-10-19 · answer to Q9 · The kit-owned collector pipeline maps Claude Code telemetry names (`claude_code.*`, `query_source`, and so on) to neutral attribute names before forwarding, so the engine sees no Claude Code terms. If the transforms get unwieldy, fall back to an engine ingestion adapter.
- 2026-10-19 · answer to Q10 · Accepted as an MVP risk: the evaluator sandbox holds the owner's `setup-token` and has egress to Anthropic's API hosts only, with spec 005's isolation flags and read-only tools. A runner-side token proxy becomes a "Next" item.
- 2026-10-19 · answer to Q11 · The runner uses a fine-grained, read-only repository token from the MVP on, even though `sample-crm` is public (not the suggested anonymous clone).
- 2026-10-19 · answer to Q12 · If any council member's runner job cannot run, the whole step is *not evaluated*. No partial median and no retry.
- 2026-10-19 · answer to Q13 · Eviction, preemption (`DisruptionTarget`), image pull failures, unschedulable pods, and clone failures are *not evaluated*. OOMKilled while the main command runs is *failed*, with the reason shown.
- 2026-10-19 · answer to Q14 · Binding a session acquires the run's lease for its user. Another user's binding is refused unless the owner takes over in the UI. No heartbeat in the MVP.
- 2026-10-19 · answer to Q15 · The driver token may read cross-run analysis and submit draft definition versions. It never publishes; publishing stays a UI-only human action.
- 2026-10-19 · answer to Q16 · Every mutating driver call and every human UI decision takes an idempotency key: a key reused with a different payload is rejected, and a retry returns the stored result. Requests on one run are serialized by locking the run's row per transaction. Runner dispatch goes through an outbox in the same transaction.
- 2026-10-19 · answer to Q17 · The shadow typed decisions run in parallel with the `code_quality` council, started when the step starts, each with its own engine deadline. The checkpoint never waits for them.
- 2026-10-19 · answer to Q18 · The runner returns a bounded log tail (about the last 64 KB) in the job result, and the engine stores it with the step result as provenance. Full logs are not kept in the MVP.
- 2026-10-19 · answer to Q19 · No notifications outside the UI in the MVP. The UI inbox is the only channel, and notifications go to BACKLOG "Next".
- 2026-10-19 · user · Agreed the Recommendation and decisions as drafted, including the three proposals that had been assumptions (neutrality by module dependency rules, telemetry not run state, clone before subject code). Asked for the architecture document to be written in this session.
- 2026-10-19 · session · Wrote `docs/architecture/` (README.md with components, modules, runner interface, persistence, trust boundaries; sequences.md with seven happy path sequences; decisions.md with AD-1 to AD-20). All ten Mermaid diagrams parse with Mermaid 11.4.1.
- 2026-10-19 · note from the user, then answer to Q20 · The user asked whether MCP should be its own deployment. Decided: a separate, stateless MCP gateway Deployment that calls only the engine's public HTTP driver API, forwarding the caller's driver token unchanged. It holds no state and no credential of its own. The engine keeps one deployable for everything else. This refines Q1, and the spec and the diagrams in `docs/architecture/` were updated to match.
- 2026-10-19 · note from the user, then answer to Q21 · The user asked where configurations are held in the engine modules. Decided, split by kind. Workflow configuration (definitions, versions, rubrics, scoring, typed questions, capability requirements) lives in the database, owned by `definitions`. Identity and access (workspaces, users, roles, human accounts, hashed driver tokens) lives in the database, owned by a new core module `identity`. Operational settings (runner address, evaluator default timeout, deadline margin, poll interval, provider endpoints, Jev model version) live in deployment config (a Kubernetes ConfigMap) read at startup, each owned by the module that uses it. Secrets (database credentials, Jev key) live in Kubernetes Secrets, never in the database. Operational values that affect an outcome are recorded on the result or event. This departs from INTENT Persistence for operational settings; editing them in the database with a UI goes to BACKLOG "Next". The user asked for all docs to be updated.
- 2026-10-19 · note from the user, then answer to Q22 · The user asked whether the database is relational, and whether definitions and run history fit MongoDB better since a workflow is a large YAML or JSON file. Findings, then decided: one relational database with ACID transactions across tables, row-level locks, and unique constraints, using document columns for document-shaped data. A definition version is one row holding the YAML exactly as approved, plus a parsed document column (for example PostgreSQL `jsonb`). Event and evidence payloads are document columns, and everything else is ordinary tables. **Fact**: the reference definition is 240 lines and about 8 KB (`docs/definitions/sdlc-reference.yaml`). Published versions are immutable and read whole by (workflow, version) (`docs/definitions/schema.md`). The product stays with spike 9, with PostgreSQL as the obvious candidate.
- 2026-10-19 · note from the user, then discussion, then answer to Q23 · The user said analysis and the quality view are inherently independent of the engine, and asked whether they should be a standalone backend deployment feeding the one UI. Agreed, with constraints: analysis only reads; run scores stay in the engine's `scoring` (Art. 1); and only the engine may access its persistence (INTENT Responsibilities), so analysis gets data through the engine, not the engine's tables. I first proposed a cursor feed that analysis long-polls. The user asked why it should poll instead of being notified, then pointed out that Google Cloud Pub/Sub with an outbox on the engine side ensures delivery through the queue. I conceded: a durable broker takes delivery off the engine, and the engine already has an outbox relay (Q16). Decided:
  - The engine writes events, and telemetry rows once joined to runs, to its outbox in the same transaction.
  - The relay publishes them to a broker topic, keyed by run ID.
  - The analysis service subscribes, deduplicates by event ID, and records its progress in the same transaction as its projection update.
  - Analysis builds its own derived, rebuildable store; in the MVP, a separate schema in the same database instance with separate credentials.
  - The engine also offers a paged event read for backfill and rebuilds, since broker retention is limited.
  - The broker sits behind an engine port. Its product is chosen in PLAN spikes 9 and 10: Pub/Sub on GKE, and the Pub/Sub emulator or a self-hosted broker (NATS JetStream, RabbitMQ) locally.
  - The single UI calls the engine's UI API and analysis's read API. Analysis checks credentials (human sessions, and driver tokens for reading analysis per Q15) with an introspection call to the engine, and accepts no writes.
  - Feedback-loop drafts still go to the engine.
  - The MCP gateway routes analysis read tools to analysis's API.

  The Pub/Sub details (ordering keys, an exactly-once option for pull subscriptions, retention in days) are from memory, not yet checked against current docs.
- 2026-10-19 · note from the user, then answer to Q24 · The user asked whether the engine modules should also have inbound ports. Decided: yes. The core exposes five use-case interfaces: `DriverOperations`, `HumanOperations`, `TelemetryIntake`, `EventFeed`, and `DueWork`. Inbound adapters (`driver-api`, `ui-api`, `telemetry-ingestion`, `event-read`, and a new `scheduler`) depend only on these ports, never on core internals. Each port call is one use case and one transaction (authenticate, idempotency key, run row lock, work, events and outbox rows, commit). The timer moves out of the core into the `scheduler` adapter, which calls `DueWork`. The core module keeps the outbox and deadline rules (renamed `outbox + deadlines`) and runs no threads.
- 2026-10-21 · research, then answer to Q25 · The user asked about the feasibility of Google Cloud Tasks for the cloud sandbox. Research (current Google docs, checked 2026-10-21):
  - **Cloud Tasks** is an asynchronous queue that delivers HTTP requests to endpoints you run (Cloud Run, GKE, Compute Engine, on-premises) or to App Engine. It does not run code. Delivery is at least once, ordering is not guaranteed, and a task name dedupes for up to 24 hours. The HTTP dispatch deadline is 10 minutes by default and 30 minutes at most. Tasks are at most 1 MiB, kept up to 31 days, and a queue dispatches up to 500 per second. There is no official emulator, only third-party ones. So it cannot be the sandbox; at most it could replace engine-to-runner dispatch, which the outbox and job-ID naming already cover (Q16), and it would add a GCP-only dependency to the local cluster.
  - **GKE Sandbox** isolates pods with gVisor (or microVMs) through a RuntimeClass. It is on by default in Autopilot. On Standard it needs a non-default node pool. Limits: no privileged containers; CPU and memory limits apply only if every container, sidecars included, has limits; no port-forward; no hostPath; no seccomp or AppArmor in sandboxed pods. It is recommended to block the metadata server with a NetworkPolicy.
  - **Cloud Run Jobs** run only in the second-generation environment, one microVM per instance. Task timeouts go up to 168 hours, with 0–10 retries. Up to 10 containers per instance, with start order through container dependencies (beta in YAML). Direct VPC egress works for jobs, but how egress firewall rules apply to jobs is only partly documented, and it does not run on the local cluster.

  Decided by the user: keep Kubernetes Jobs, make GKE Sandbox the target on GKE, do not use Cloud Tasks, and keep Cloud Run Jobs as a possible later runner adapter.
- 2026-10-21 · notes from the user, research, then answers (Q25 follow-ups) ·
  - **Cloud Tasks and the outbox.** The user asked whether Cloud Tasks could be used for the outbox. It cannot replace it. Enqueueing from inside the use case is a dual write that cannot join the database transaction, so state and dispatch could diverge. It can only be a delivery leg after the outbox: `DueWork` hands an outbox row to Cloud Tasks, named after the row ID so repeats are rejected for 24 hours, and Cloud Tasks calls the runner with retries, backoff, and rate limits. Those limits govern how fast jobs start, not how many run, so a concurrency cap stays in the runner. Costs: no official local emulator, and the runner needs an ingress endpoint with OIDC auth for Google's infrastructure. The scheduler still drains the outbox, polls results, and fires deadlines. Pub/Sub fits events better (ordering keys, fan-out). Decided: Cloud Tasks only as an optional delivery leg after the outbox, behind `RunnerPort`, on GKE and not in the MVP.
  - **Cloud Run functions as the sandbox.** The user asked about them. Research (current Google docs, 2026-10-21): functions are deployed from source and built into a Cloud Run service. HTTP functions time out after at most 60 minutes and event-driven ones after 9. An instance handles up to 1,000 concurrent requests and is reused across requests. Multiple containers and sidecars are not documented for functions. Against the sandbox needs:
    - a check runs its own image pinned by digest, which functions cannot;
    - a sandbox is thrown away after each job, but function instances are reused, so one job's subject code could affect the next;
    - service sidecars (PostgreSQL) are not documented for functions;
    - there is no local equivalent.

    Decided: rejected as a sandbox. Cloud Run Jobs (image-based, run to completion, sidecars) remain the only Cloud Run option, as a possible later adapter.
- 2026-10-21 · notes from the user, then answer to Q26 · The user observed that git already syncs the change locally and to the runners, and asked what evidence still needs submitting. Then the user asked whether the UI could read git and diffs. Finally the user asked why the diff could not be pulled inside the cluster to serve as Jev's input (Jev is hosted, so the pulling happens in a runner job). Decided:
  - **Git is the evidence channel.** The reference workflow needs no driver-submitted evidence; each transition request carries only the revision.
  - `diff` is dropped: evaluators diff their own clone, and `diff_present` becomes a runner `command` check.
  - `requirements_doc` becomes a file the driver commits at a path the definition declares; a runner check confirms it exists at the revision.
  - `local_test_summary` is dropped; the approver sees the runner's `tests_green` result.
  - `merge_receipt` is replaced by a runner check that the target branch contains the reviewed revision.
  - **The UI reads git in the browser**, straight from the git host and pinned to the reviewed revision, through a view the definition declares. For the MVP that is a link to the compare page. The human uses their own git host login, or none for public repositories, and no server holds a credential for it.
  - **Typed-decision input:** the checkpoint dispatches a small runner job that clones the revision and returns a bounded text packet (the diff and the requirements file). The engine's adapter sends that packet to Jev. The Jev key stays in the engine (Art. 4), and the runner never calls the engine (Q2). The packet is input only, never gating evidence, and is stored with the typed decision records.
  - `submit_evidence` stays in the engine as a neutral operation for workflows that do not use git.
  - Sequence 2 drops out of the SDLC happy path. This also settles the spike 8 input on whether a runner evaluator reads the driver's diff: it diffs its own clone.
- 2026-10-21 · note from the user, answer to Q27 · The user took Laya out of the MVP: from past spikes, Laya would need training to be operational, which is too costly now. Jev is the only provider. Laya stays in the intent as a later provider; fine-tuning stays in BACKLOG "Next". `laya-serve` leaves the MVP components and repository layout.
- 2026-10-21 · note from the user, then answer to Q28 · The user wants a decision tree for Jev: depending on output values, ask follow-up questions or take other decisions. I explained it belongs in the definition, walked by the engine core, not in the DecisionEngine adapter. The user asked for it in the MVP and to change whatever needs changing. Decided:
  - **Declared in the definition.** A typed decision may carry a `then` list of branches. Each branch has a `when` condition on the answer (`label_in`, `min_confidence`, `max_confidence`, using the provider's answer confidence) and either `ask` (a follow-up question, which may have its own `then`) or `conclude` (a named conclusion).
  - **Walking rules.** Branches are checked in order, and the first match wins. A *not evaluated* answer ends the tree at that node.
  - **Limits.** At most 3 questions on any path and 5 per tree. No question repeats on a path.
  - **Questions.** Typed questions gain an optional `type` (`choice`, the default, or `noul`, answered `true` or `false`). Only the root question must have the step's band IDs as labels.
  - **The walk.** The core `typed-decisions` module walks the tree and asks one question per DecisionEngine call. One runner input packet serves the whole tree (the union of its inputs). It records every node, answer, branch taken, and conclusion, and gives each question its own deadline.
  - **Effect on the run.** The tree stays in shadow mode: conclusions are recorded and shown in the quality view, and change nothing in the run. Branches that act on the run (`advisory`, `bounded_control`) stay out of the MVP, and humans still decide human steps (Art. 2).
  - **First tree.** Spike 007's battery: when `code_quality_band` is `poor` or `weak`, or its confidence is low, ask the narrow `has_defect` question. A confident `true` concludes `likely_defect`.
- 2026-10-21 · note from the user (Q28) · The decision tree definition belongs in the `definitions` module. Recorded: `definitions` owns tree definitions as part of each definition version (validation by rules 12, 17, and 18, versioning, human approval, pinning). `typed-decisions` reads the tree from the run's pinned version and owns only the walk and its records.

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1

- **Decision** (Q1): the engine is one deployable with internal modules. Telemetry ingestion, runner dispatch, the driver API, and the UI API all run in it. The server-side deployables are engine, UI, runner, `laya-serve`, collector, and database.
- **Decision** (Q4): the engine owns human login, so the UI deployable is a static or thin client of the engine's UI API with no authority of its own.
- **Decision** (Q9): the collector is a kit-owned component in terms of configuration. Its pipeline holds the Claude Code to neutral mapping, next to the redaction from spec 006.
- **Decision** (Q1, Q6; refined by Q24, where the timer is the `scheduler` inbound adapter calling `DueWork`): durable deadlines and the outbox relay live in the engine, as engine-side timers over its own tables. There is no separate scheduler or queue component.
- **Fact** (spec 005, spec 006, spec 007): the laptop reaches three endpoints, all with the driver token: the engine's MCP endpoint, the engine's HTTP API for hooks, and the collector's OTLP/HTTP receiver. The browser reaches the UI and the engine's UI API with a human session.
- **Decision** (Q20, refines Q1): the MCP server is its own Deployment, a stateless MCP gateway. It translates MCP tool calls into calls to the engine's public HTTP driver API, the same API the hooks call. The engine's driver API module is HTTP only. The server-side deployables are engine, MCP gateway, UI, runner, `laya-serve`, collector, and database. The laptop still reaches three endpoints: the MCP gateway, the engine's HTTP driver API (hooks), and the collector.
- **Decision** (Q21): configuration is split by kind.
  - Workflow configuration is in the database, owned by `definitions`.
  - Identity and access is in the database, owned by a new core module `identity`, which `driver-api` and `ui-api` use to authenticate.
  - Operational settings are in a Kubernetes ConfigMap read at startup, each owned by the module that uses it (`runner-client`, `systemone`, `outbox + deadlines`, and, since Q24, `scheduler`).
  - Secrets are in Kubernetes Secrets.
- **Decision** (Q22): the database component is one relational database. The product (PostgreSQL is the obvious candidate) is chosen in PLAN spike 9.
- **Decision** (Q23, refines Q1): analysis (the quality view, cross-run measures, and weakest-step detection for the feedback loop) is its own Deployment, the analysis service. It leaves the engine. A message broker is a new component between them. The server-side deployables are engine, MCP gateway, analysis service, UI, runner, `laya-serve`, collector, broker, and database. The engine keeps run scoring, and the UI stays one UI over two read APIs.
- **Decision** (Q24): the engine is hexagonal on both sides. Inbound adapters call inbound ports (use-case interfaces implemented by the core). The core calls outbound ports, which outbound adapters implement. The five inbound ports are `DriverOperations`, `HumanOperations`, `TelemetryIntake`, `EventFeed`, and `DueWork`. A new `scheduler` inbound adapter (the timer) calls `DueWork`, and the core module `outbox + deadlines` keeps the rules but runs no threads.
- **Decision** (Q27): Laya is not in the MVP. `laya-serve` leaves the MVP components, and the `systemone` adapter talks to Jev only. Laya stays a later DecisionEngine provider behind the same port.
- **Decision** (Q28): a new core module, `typed-decisions`, walks decision trees declared in the definition. `checkpoints` starts it when a shadowed step runs. The DecisionEngine port and the `systemone` adapter stay at one question per call.
- **Decision** (Q28, user note): the decision tree definition belongs to the core `definitions` module, as part of each definition version. `typed-decisions` reads it from the run's pinned version and owns only the walk and its records.

### G2

- **Decision** (Q2): the engine polls the runner for job status and results. Trust is one-way: the engine calls the runner, and the runner never calls the engine.
- **Decision** (Q16): the engine dispatches with its own job ID, and the runner names the Kubernetes Job after it, so a repeated dispatch is harmless. That Kubernetes answers a repeated name with AlreadyExists is an inference still to confirm (Follow-ups).
- **Decision** (Q6): a runner-wide default timeout bounds `agent_evaluator` jobs, which have no `timeout` key. The engine sets its own deadline per job (the job's timeout plus a margin). When that deadline passes without a result, the engine records the step *not evaluated* and cancels the job.
- **Decision** (Q12): a council step is *not evaluated* if any member's job cannot run. A council always has its declared size, and *not evaluated* carries no deduction (spec 003 Q27).
- **Decision** (Q11): the runner's source adapter clones with a fine-grained, read-only repository token, so the job packet path is the same for public and private subjects.
- **Decision** (Q13): the runner reads pod and container state, not only the Job condition, to classify outcomes. Infrastructure causes (`DisruptionTarget` eviction or preemption, image pull failure, unschedulable, clone failure, service never ready, sandbox never started) are *not evaluated*. The subject's own failures (non-zero exit, past `timeout`, OOMKilled during the main command) are *failed*, with the reason shown. **Fact** (Kubernetes Jobs docs): a Job-level `DeadlineExceeded` cannot tell "never started" from "ran too long", which is why pod state is needed.
- **Decision** (Q18): the job result carries the outcome, the classification reason, provenance (runner, image, command, revision), evaluator usage, the structured output for evaluators, and a bounded log tail (about the last 64 KB). The engine stores the tail with the step result, and full logs are not kept in the MVP.
- **Decision** (Q25): sandboxes stay Kubernetes Jobs, the same on the local cluster and on GKE. Google Cloud Tasks is not used. **Fact** (Cloud Tasks docs, 2026-10-21): it only delivers HTTP requests (at least once, 30-minute maximum dispatch deadline) and runs no code. Cloud Run Jobs stay a possible later runner adapter behind the same runner interface; they have no local equivalent.
- **Decision** (Q25 follow-up): Cloud Tasks may only be an optional delivery leg after the outbox, behind `RunnerPort` (on GKE, not in the MVP). It never replaces the outbox, because enqueueing cannot join the database transaction.
- **Decision** (Q25 follow-up): Cloud Run functions are rejected as a sandbox. **Fact** (Cloud Run functions docs, 2026-10-21): they are built from source rather than running a declared image, reuse instances across requests (not throwaway), document no sidecars, and cap HTTP calls at 60 minutes.
- **Decision** (Q26): runner jobs gain a third kind, the **input packet job**. It clones the revision, resolves the subject inputs the definition declares (for example the diff from the base and a file at a path), and returns them as bounded text in its result. It is used for typed-decision input only, never as evidence a step gates on.

### G3

- **Decision** (Q8): for an exhaustion escalation resolved with "rework", the engine commits the rework transition on the owner's UI decision. The rework edge has no checkpoint, and the driver learns of it from `status`. The other resolutions (extend the budget, abandon) are also engine-side changes on a human UI decision.
- **Decision** (Q15): in the feedback loop, the driver token may read cross-run analysis and submit draft definition versions. Approving a draft publishes it, and that happens only in a human UI session.
- **Decision** (Q17): shadow typed decisions start in parallel with the `code_quality` council, each with its own engine deadline, and the checkpoint never waits for them.
- **Decision** (Q16): every mutating call in every sequence carries an idempotency key, and runner dispatch is written to an outbox in the same transaction as the step change.
- **Fact**: the decisions above, together with the Known items in the Briefing, fix each happy path sequence. They are drawn in the architecture document (Deliverables).
- **Decision** (Q23): a new flow, event publication. A state change writes its events, and an outbox row for each, in one transaction. The relay publishes to the broker topic keyed by run ID, and the analysis service consumes, deduplicates, and updates its projections. The feedback loop reads analysis from the analysis service (through the MCP gateway) and submits drafts to the engine.
- **Decision** (Q26): the "submit evidence" sequence leaves the SDLC happy path. The driver commits and pushes, and requests transitions with the revision. In the checkpoint sequence, an input packet job runs before the Jev shadow call, and the approval screen links to the git host at the reviewed revision.
- **Decision** (Q28): in the checkpoint sequence, the shadow path is one input packet job, then a walk of the tree: ask, record, evaluate the branches, and ask the next question or conclude. All of it runs in parallel with the council and is never awaited.

### G4

- **Decision** (Q2): runner results reach the engine when the engine polls, so a checkpoint waiting on the runner moves forward on the engine's own poll. That is reacting to work the engine dispatched itself (Art. 9).
- **Decision** (Q6): an attempt cannot hang. Every runner job has an engine-side deadline, and passing it records *not evaluated*, so a stuck runner never holds the run's one open attempt forever.
- **Decision** (Q7): at a human gate the driver session ends. A later session (prompted, for example, with "Continue.") resumes from the status injected at SessionStart. Nothing waits or polls while a human decides.
- **Decision** (Q8): what moves a waiting checkpoint forward is always an engine-side event: a human decision recorded from the UI, a runner result the engine polled, or a deadline the engine set. The driver and the UI learn about it by pulling.
- **Decision** (Q19): no notifications outside the UI in the MVP. The UI inbox is the only channel for human steps and escalations, and notifications go to BACKLOG "Next".

### G5

- **Decision** (Q1): one deployable means state, events, and runner dispatch records can share one database transaction.
- **Decision** (Q5): state tables are the source of truth, and every state change writes its events in the same transaction. Replay is a check, not the read path: it rebuilds state and score from the events alone and must match the tables (the milestone 2 replay test). So events must carry everything replay needs, for example the revision on each transition request.
- **Decision** (Q14): the MVP records leases. Binding a session acquires the run's lease for that session's user, and another user's binding is refused unless the owner takes over in the UI. Every lease holder is recorded as an event, so separation of duties can exclude "anyone who ever held a lease on the run". No heartbeat (still "Next").
- **Decision** (Q16): every mutating driver call and every human UI decision takes an idempotency key, stored with its result. The same key with a different payload is rejected, and a retry returns the stored result (IETF Idempotency-Key draft-07 semantics). Requests on one run are serialized by locking the run's row for the transaction. Runner dispatch uses a transactional outbox, and the runner names the Kubernetes Job after the engine's job ID, so delivering a dispatch twice is harmless.
- **Decision** (recommendation agreed): telemetry rows are not run state. They are joined to runs but not replayed, and replay covers runs, attempts, step results, decisions, leases, and scores.
- **Decision** (Q21): operational values that affect an outcome (the evaluator default timeout, the deadline margin, the provider model version) are recorded on the result or event they affected. A replay then gives the same state and score after a setting changes. Workflow configuration is pinned by version, as before.
- **Decision** (Q22): one relational database. The architecture depends on:
  - ACID transactions across tables (state, events, idempotency record, and outbox together; Q5, Q16, Art. 7);
  - row-level locks (one per run, Q16);
  - unique constraints (idempotency keys; one active run per workflow and subject key);
  - joins and aggregates across runs, for the quality view, evaluator agreement, and telemetry correlation.

  Document-shaped data goes in document columns: a definition version stores its YAML exactly as approved plus a parsed document, and event and evidence payloads are documents. **Fact**: definitions are small (the reference workflow is about 8 KB), immutable once published, and read whole by (workflow, version), so they need no document database. Rejected alternatives: MongoDB for everything, whose weakest point is analysis across runs, a core pillar; and MongoDB for definitions only, which means two systems and no single transaction when a version is published or pinned to a run.
- **Decision** (Q23): the outbox carries two kinds of work: runner dispatch (Q16) and events to publish. Analysis's store is derived and rebuildable. It is fed live by the broker and rebuilt from the engine's paged event read when needed, because broker retention is limited. Delivery is at least once, so analysis deduplicates by event ID and stores its progress in the same transaction as each projection update.
- **Decision** (Q24): the transaction boundary is the inbound port call. Each use case authenticates, takes the idempotency key, locks the run row, does its work, writes events and outbox rows, and commits once.
- **Decision** (Q28): every node of a tree (its question, answer, confidence, the branch taken, any conclusion) is a record with an event, and the input packet is stored once per tree. A replay shows why each follow-up was asked.

### G6

- **Decision** (Q2): the runner holds no engine credential, and nothing in the runner or its sandboxes can reach the engine.
- **Decision** (Q4): human sessions are issued by the engine. Driver tokens and human sessions are separate credential types checked by the engine, and only human sessions can carry approval scopes. The UI deployable holds no credential that can approve.
- **Decision** (Q10), accepted MVP risk: evaluator sandboxes hold the owner's `setup-token` (`CLAUDE_CODE_OAUTH_TOKEN`) while reading agent-written code. Their egress is limited to Anthropic's API hosts, with spec 005's isolation flags and Read, Grep, and Glob only. Check sandboxes keep the Maven proxy as their only egress (spec 002). A runner-side token proxy is a "Next" item.
- **Decision** (Q11): the runner holds a fine-grained, read-only repository token, even though `sample-crm` is public. **Decision** (recommendation agreed): the source adapter clones in a step before any subject code runs, for example an init container, so the token is not readable in the sandbox once the subject's code runs.
- **Decision** (Q15): driver token scopes are run operations on the user's own runs, reading cross-run analysis, and submitting draft definitions. They never include approvals, overrides, human step decisions, or publishing.
- **Decision** (Q14): a driver token can act only on a run whose lease its user holds. Taking over a lease is a human UI action by the owner.
- **Decision** (Q20): the MCP gateway holds no credential of its own. It forwards the caller's driver token unchanged, and the engine authenticates and authorizes every call as before. If the engine is unreachable, the gateway returns an error, so the tool call fails closed.
- **Decision** (Q21): secrets (database credentials, the Jev key) are Kubernetes Secrets, never stored in the database. Driver tokens are stored hashed in `identity`.
- **Decision** (Q23): the analysis service holds its own schema credentials and a broker subscription credential, and has no access to the engine's tables. It authenticates callers by introspection against the engine and accepts no writes. The engine holds a broker publish credential.
- **Decision** (Q25), target: on GKE, sandbox pods run under GKE Sandbox (gVisor RuntimeClass), with limits on every container including service sidecars, and a NetworkPolicy blocking the metadata server. **Fact** (GKE Sandbox docs, 2026-10-21): it gives kernel-level isolation for untrusted code, is on by default in Autopilot, and does not allow privileged containers, which sandboxes never use.
- **Decision** (Q26): the UI reads git in the browser with the human's own git host login (none for public repositories). No server-side credential is added; the repository token stays with the runner (Q11).

### G7

- **Decision** (Q1): neutrality is enforced inside one deployable, so by module dependency rules, not network boundaries.
- **Decision** (Q3): protected-action detection lives in the driver kit. The kit's hook maps a tool call (for example `gh pr merge`) to a neutral action name and revision, and the engine sees no commands, git, or Claude Code terms. This removes the first neutrality leak found in the briefing.
- **Decision** (Q9): Claude Code telemetry names are mapped to neutral names in the kit-owned collector config, so engine ingestion stays provider-neutral. This removes the second neutrality leak found in the briefing.
- **Decision** (recommendation agreed, follows from Q1): neutrality is enforced by module dependency rules checked in the build. The engine core may not depend on adapter modules, and no SDLC, git, or Claude Code names may appear in core packages. The non-SDLC toy workflow test stays BACKLOG "Next". The rule-checking tool is chosen in spike 9.
- **Decision** (Q20): MCP, a fast-changing agent protocol, sits outside the engine's process like the collector. The engine's HTTP driver API is the one provider-neutral driver contract, and MCP version changes (2025-11-25 stateful, 2026-07-28 stateless; Java SDK 2.1 and 2.2) touch only the gateway.
- **Decision** (Q21): provider names and endpoints are operational settings of the `systemone` adapter and stay out of core configuration. Definitions name providers by design (schema rule 12).
- **Decision** (Q23): the broker sits behind an engine port (an event publisher port, like `RunnerPort`), so the product (Pub/Sub, an emulator, or a self-hosted broker) stays out of core code.
- **Decision** (Q24): module rules allow inbound adapters to depend only on inbound ports, and the core only on outbound ports. A new transport (a CLI, or MCP in process) is one more inbound adapter on the same ports.
- **Decision** (Q28): the tree is workflow knowledge, so it lives in the definition (versioned, approved by a human, open to feedback-loop proposals), not in the provider adapter. The condition form is declarative and fixed (`label_in`, `min_confidence`, `max_confidence`), not an expression language.

## Recommendation and decisions

<!-- Drafted when every goal question is answered, and agreed with the user. -->

| Decision | Choice | Alternatives considered | Why |
| --- | --- | --- | --- |
| Engine deployables | One engine deployable with internal modules; MCP is a separate gateway (next row) | Telemetry ingestion as its own service; several services on one database | One writer and one transaction for state, events, and dispatch (Art. 1, 7); one user (Q1) |
| MCP server | A separate, stateless MCP gateway Deployment that calls the engine's public HTTP driver API with the caller's driver token | MCP as a module in the engine | One driver contract (the HTTP API the hooks already use); MCP churn stays out of engine releases; the gateway holds no state or credential, so Art. 1 and fail closed hold (Q20) |
| Configuration | Split by kind: workflow configuration (`definitions`) and identity (new `identity` module) in the database; operational settings in a ConfigMap, owned by the module that uses them; secrets in Kubernetes Secrets; outcome-affecting values recorded on results | Everything but secrets in the database (INTENT Persistence) | Simplest for one operator with no settings UI; replay still holds; the database and UI path is "Next" (Q21) |
| Database model | One relational database (ACID across tables, row locks, unique constraints), with document columns for definition YAML and parsed form, event payloads, and evidence payloads | MongoDB for everything; MongoDB for definitions plus a relational database for runs | Definitions are small, immutable, and read whole; run history is relational and analysed across runs; one system and one transaction (Q22) |
| Analysis | A separate analysis service with its own derived store, fed by a broker topic the engine publishes to from its outbox; a paged engine event read for rebuilds; auth by engine introspection; no writes | Analysis as an engine module; a cursor feed the service long-polls; read-only access to the engine's tables | Analysis is read-only and changes on its own cadence; a durable broker ensures delivery without burdening the engine; the persistence rule and Art. 1 hold (Q23) |
| Inbound ports | Use-case interfaces (`DriverOperations`, `HumanOperations`, `TelemetryIntake`, `EventFeed`, `DueWork`) between inbound adapters and the core; the timer is a `scheduler` inbound adapter | Edges call core modules directly; the timer inside the core | One surface for every transport, one transaction per use case, enforceable module rules, tests without HTTP (Q24) |
| Cloud sandbox | Kubernetes Jobs everywhere; GKE Sandbox (gVisor) as the target on GKE; Cloud Run Jobs as a possible later runner adapter; Cloud Tasks at most an optional delivery leg after the outbox | Google Cloud Tasks as sandbox or as outbox; Cloud Run functions (source-built, reused instances); Cloud Run Jobs now | Same Jobs locally and on GKE; stronger isolation for untrusted code on GKE; Cloud Tasks would only duplicate the outbox (Q25) |
| Evidence channel | Git: the transition request carries only the revision; runner checks replace `diff_present` and `merge_recorded`; requirements are a committed file; the UI reads git in the browser through a declared view; Jev input comes from a runner input packet job | Driver-submitted `diff`, `requirements_doc`, `local_test_summary`, `merge_receipt`; a UI backend or the engine reading git | One source of truth for content; checks become engine-verified; no new credentials; Art. 4 and Q2 hold (Q26) |
| Decision providers in the MVP | Jev only | Laya and Jev (spike 007) | Laya needs training to be useful, too costly now (Q27) |
| Decision trees for typed decisions (MVP) | Declared in the definition (`then` branches with `when` conditions, `ask` or `conclude`); walked by a core `typed-decisions` module; one question per DecisionEngine call; shadow only | Tree in the DecisionEngine adapter; in BACKLOG "Next"; branches that act on the run | Workflow knowledge stays in the definition; provider-neutral; every node replayable and measurable; Art. 2 holds (Q28) |
| Runner results | The engine polls the runner | Runner callback with a runner-only credential; one long-held request per job | Trust stays one-way, and the runner holds no engine credential; 15-minute checks make poll latency irrelevant (Q2) |
| Protected actions | The kit's hook maps a tool call to a neutral action name and revision; the engine checks action, stage, and revision | Match patterns in the definition; the engine parses commands (spec 005) | Removes git and Claude Code from the engine (Art. 3); GitHub branch protection stays the real gate (Q3) |
| Human authentication | The engine owns human login and sessions; the browser calls the engine's UI API | The UI backend asserts the user with a service credential; an external identity provider | No service credential can ever approve (Art. 2) (Q4) |
| Source of truth | State tables, with events in the same transaction; replay is a check | Event sourcing; an audit log that is never replayed | Meets Art. 7 and the milestone 2 replay test at the lowest cost (Q5) |
| Hung jobs | A runner default timeout for evaluators, plus an engine deadline per job that records *not evaluated* | `timeout` on `agent_evaluator`; no bound | Fails closed, and an attempt never hangs; no schema change (Q6) |
| Human gate | The driver session ends and a later session resumes from injected status | Poll inside the session; `/loop` re-checks | Proven in spec 005 G9 and costs no usage while waiting (Q7) |
| Exhaustion "rework" | The engine commits the rework transition on the owner's UI decision | A directive in `status` that the driver acts on; depends on the edge | Art. 9 allows changes on a human UI decision, and rework edges have no checkpoint (Q8) |
| Telemetry neutrality | The kit-owned collector config maps Claude Code names to neutral names | An engine ingestion adapter; map at query time | Provider specifics stay in kits; the engine adapter is the fallback (Q9) |
| Evaluator token | Accepted risk: the sandbox holds the `setup-token`, with egress to Anthropic only and read-only tools | Runner-side token proxy (unverified); a packet built outside the sandbox | Your own public repository, read-only tools; the proxy goes to "Next" (Q10) |
| Repository access | A fine-grained, read-only token, used in a clone step before any subject code runs | Anonymous clone; a GitHub App | Your choice; one code path for public and private subjects (Q11) |
| Council member cannot run | The whole step is *not evaluated* | Majority median; retry once | Simplest fail-closed rule, and a council keeps its declared size (Q12) |
| Outcome classification | Infrastructure causes are *not evaluated*; non-zero exit, timeout, and OOMKilled are *failed* | Everything infrastructure-like *not evaluated*; decide after a prototype | The subject's code ran and exceeded published limits; pod state separates the cases (Q13) |
| Leases | Binding acquires the lease for its user, and only the owner can take over in the UI | No leases; a lease per session | Gives separation of duties a precise record with no heartbeat (Q14) |
| Driver token in the feedback loop | Read analysis and submit drafts, never publish | Read only; a separate token | Publishing stays a human UI action (Q15) |
| Idempotency and serialization | Keys on every mutation and human decision; a run-row lock; an outbox for dispatch; Jobs named after job IDs | Keys on transitions only; defer to spike 9 | Retries are safe end to end (Q16) |
| Shadow typed decisions | Run in parallel with the council, with their own deadlines, never awaited | After the council | Shadow mode never delays the checkpoint (Q17) |
| Runner logs | A bounded tail (about 64 KB) in the result, stored with the step result | Full logs; logs kept by the runner | Enough provenance with no log store (Q18) |
| Notifications | None outside the UI in the MVP | Email; Slack | Happy path first; "Next" (Q19) |
| Neutrality enforcement | Module dependency rules checked in the build; the toy workflow test stays "Next" | Separate deployables; a toy workflow test now | Follows from one deployable; the tool is chosen in spike 9 (G7) |

## Deliverables

- [x] Architecture document in `docs/architecture/` with component and sequence diagrams, and a short decision record for each key choice
- [x] Every goal question answered, and the recommendation agreed with the user
- [x] BACKLOG.md items settled by this spike updated

## Follow-ups

<!-- New specs, BACKLOG items, experiments, or open questions this spike created. -->

- Update spec 005's write-up and BACKLOG "Hooks to engine" when this spike closes: the kit maps tool calls to action names, and the engine no longer reads the full command (Q3).

- Update spec 001's Findings G4 and `docs/concepts/` when this spike closes: the engine commits the rework transition on the owner's decision (Q8).

- BACKLOG "Next": a runner-side token proxy, so the evaluator's `setup-token` never enters the sandbox (Q10).

- BACKLOG "Next": notifications outside the UI for waiting human steps and escalations (Q19).
- BACKLOG "Next": keeping full runner job logs (Q18).
- PLAN spike 8: the HTTP driver API is the one driver contract, and MCP tool schemas mirror it in the gateway (Q20).
- PLAN spike 9: pick the gateway's MCP SDK and version (Java SDK 2.1.0 stateful or 2.2.0 stateless, or another language) (Q20).
- BACKLOG "Next": operational settings stored in the database and edited in the UI (Q21).
- PLAN spikes 9 and 10: choose the broker product (Pub/Sub on GKE; the emulator or a self-hosted broker locally) and check the Pub/Sub details recorded under Q23 against current docs (Q23).
- PLAN spike 8: the analysis read API and the engine's introspection and paged event read join the driver API contract (Q23).
- BACKLOG "Next": GKE Sandbox (gVisor) for sandbox pods on GKE, and Cloud Run Jobs as an optional runner adapter (Q25).
- Spike 003's draft (`docs/definitions/`) updated for Q26 and Q27: a `subject` block (base, inputs, view), templates in `command` checks, `evidence_types` optional, Jev the only provider. The milestone 1 validator and fixtures follow the updated draft.
- Assumption to confirm when the test bed runs: `merge_recorded` checks that the target branch contains the reviewed revision, which needs merges that keep that commit (a merge commit, not squash or rebase) (Q26).
- Take in spike 008's result (Jev telemetry) when it closes: the DecisionEngine call telemetry in `docs/architecture/` is a placeholder until then.
- Confirm that creating a Kubernetes Job with an existing name returns AlreadyExists (AD-16), in the classification prototype.

Where each follow-up went (verification, 2026-10-21):

- Spec 005, spec 001, `docs/concepts/`, and BACKLOG "Hooks to engine": updated now (Q3, Q8).
- BACKLOG "Next": evaluator token proxy (Q10), notifications (Q19), full runner logs (Q18), operational settings in the database (Q21), GKE Sandbox and the Cloud Run Jobs adapter (Q25), and trees beyond shadow (Q28).
- Inputs added to the BACKLOG entries for PLAN spike 8 (driver API contract), spike 9 (stack: MCP SDK, broker, rule checker), and spike 10 (local setup: workloads, egress, merge commits on `sample-crm`, the Job classification prototype and the AlreadyExists check).
- Spike 003's draft: already updated in this spike.
- Spike 008: a note is in its Inbox; `docs/architecture/` takes in its result when it closes.
- The researcher's things to try: covered by the spike 9 and 10 inputs above, or dropped (the diagram draft was done in this spike).

Things to try, proposed by the researcher (not run in this spike; candidates for PLAN spikes 9 and 10 or the Phase 0 scaffolding):

- Job classification prototype on the local cluster: exit 0, exit 1, deadline, sidecar never ready, bad image digest, OOMKilled, disrupted pod. Record the Job, pod, and container states (settles Q13, G2; needs Kubernetes 1.33 or later; about half a day). Includes a check that a repeated Job name returns AlreadyExists.
- Stateless MCP trial with MCP Java SDK 2.2.0-M1 against Claude Code (about 2 h).
- Evaluator token proxy: `claude -p` with `ANTHROPIC_BASE_URL` pointing at a runner-side proxy that adds the OAuth token (Q10 option 2; about 2 h).
- Paper replay of `docs/definitions/example-run.md` as an event list (Q5, G5; about 2 h).
- Draft Mermaid component diagram and one checkpoint sequence for review before Q1, Q2, Q7 (about 2–3 h).

## Changelog

- 2026-10-19 Created, filled from BACKLOG.md, briefing and questions prepared; status in-progress.
- 2026-10-19 Interview complete: Q1–Q19 answered, G1–G7 answered, recommendation agreed, architecture document written in `docs/architecture/`. Ready for /sdd-verify.
- 2026-10-19 Reopened for Q20: MCP becomes a separate gateway Deployment; Findings, Recommendation, and `docs/architecture/` updated.
- 2026-10-19 Reopened for Q21: configuration placement and a new `identity` module. Spec, `docs/architecture/`, INTENT Persistence, and BACKLOG "Next" updated. Q20's MCP gateway also reflected in INTENT (deployables table, Driver integration), BACKLOG "MCP server", and the CLAUDE.md planned layout (`mcp-gateway/`).
- 2026-10-19 Reopened for Q22: the database is relational, with document columns. Spec, `docs/architecture/`, BACKLOG "Database", INTENT Persistence, and CLAUDE.md updated.
- 2026-10-19 Reopened for Q23: analysis becomes its own service fed by a broker. Spec, `docs/architecture/`, INTENT, BACKLOG, and CLAUDE.md updated.
- 2026-10-19 Reopened for Q24: inbound ports and a `scheduler` inbound adapter. Spec and `docs/architecture/` updated.
- 2026-10-21 Reopened for Q25: Cloud Tasks researched and not used; GKE Sandbox as target; Cloud Run Jobs as a later adapter. Spec, `docs/architecture/`, and BACKLOG updated.
- 2026-10-21 Q25 follow-ups: Cloud Tasks only as an optional delivery leg after the outbox; Cloud Run functions rejected as a sandbox.
- 2026-10-21 Reopened for Q26 (git as the evidence channel) and Q27 (Laya out of the MVP). Spec, `docs/definitions/`, `docs/architecture/`, `docs/concepts/`, `docs/test-bed/README.md`, BACKLOG, PLAN, INTENT, CLAUDE.md, and the constitution updated.
- 2026-10-21 Reopened for Q28: typed-decision trees in the MVP. Spec, `docs/definitions/`, `docs/architecture/`, BACKLOG, PLAN, and INTENT updated.
- 2026-10-21 Verified and closed: G1–G7 and Q1–Q28 answered, recommendation agreed, BACKLOG settled items updated (State and events, Auth, Result delivery, Runs and repositories, Evidence), follow-ups filed, PLAN spike 7 ticked.
