# Architecture decisions

From spike 010 (`specs/010-target-architecture/`). Each record names the question that settled it, and the spike's Log holds the user's answer. All were decided by the user on 2026-10-19.

## AD-1 · One engine deployable

- **Decision:** the engine is one deployable with internal modules. Telemetry ingestion, runner dispatch, the HTTP driver API, and the UI API all run in it. MCP does not: see AD-21. Analysis does not: see AD-24.
- **Alternatives:** telemetry ingestion as its own service; several services around one database.
- **Why:** one writer and one transaction for state, events, and dispatch (Art. 1, 7), and there is one user. Neutrality is enforced by module dependency rules (AD-20). (Q1)

## AD-2 · The engine polls the runner

- **Decision:** the engine dispatches, polls, and cancels runner jobs. The runner never calls the engine.
- **Alternatives:** a runner callback with a runner-only credential; one long-held request per job.
- **Why:** trust stays one-way, and the runner holds no engine credential (INTENT). The engine notices an unreachable runner and records *not evaluated*. With 15-minute checks, poll latency does not matter. (Q2)

## AD-3 · The kit maps protected actions

- **Decision:** the driver kit's hook maps a tool call to a neutral action name and a revision. The engine checks only the action, the stage (`allowed_in`), the revision, and the lease.
- **Alternatives:** match patterns in the definition; the engine parses commands (spec 005).
- **Why:** keeps git and Claude Code out of the engine (Art. 3). GitHub branch protection stays the real gate. This replaces spec 005's design. (Q3)

## AD-4 · The engine owns human login

- **Decision:** the engine issues human sessions, and the browser calls the engine's UI API with them. The UI deployable holds no credential that can approve.
- **Alternatives:** the UI backend asserts the user with a service credential; an external identity provider.
- **Why:** human decisions can only come from human sessions (Art. 2), even if the UI is compromised. (Q4)

## AD-5 · State tables are the source of truth

- **Decision:** state tables are authoritative, and events are written in the same transaction. Replay is a check that rebuilds state and score from the events and compares them with the tables.
- **Alternatives:** event sourcing; an audit log that is never replayed.
- **Why:** meets Art. 7 and the milestone 2 replay test at the lowest cost. (Q5)

## AD-6 · Every runner job has a bound

- **Decision:** a runner-wide default timeout bounds evaluator jobs. The engine sets a deadline per job (the job's timeout plus a margin) that records *not evaluated* and cancels the job.
- **Alternatives:** a `timeout` key on `agent_evaluator`; no bound.
- **Why:** fails closed, an attempt never hangs, and it needs no schema change. (Q6)

## AD-7 · Driver sessions end at a human gate

- **Decision:** at a human gate the driver session ends, and a later session resumes from the status injected at SessionStart.
- **Alternatives:** poll inside the session; `/loop` re-checks.
- **Why:** proven in spec 005 G9, and it costs no usage while waiting. (Q7)

## AD-8 · The engine commits an owner-directed rework

- **Decision:** when the owner resolves an exhaustion escalation with "rework", the engine commits the rework transition itself.
- **Alternatives:** a directive in `status` that the driver acts on; depends on whether the edge has a checkpoint.
- **Why:** Art. 9 allows changes on a human UI decision, and rework edges have no checkpoint. This replaces spec 001's assumption. (Q8)

## AD-9 · The collector maps telemetry names

- **Decision:** the kit-owned collector config maps Claude Code telemetry names to neutral names before forwarding.
- **Alternatives:** an engine ingestion adapter; map at query time.
- **Why:** provider specifics stay out of the engine. An engine adapter is the fallback if the transforms get unwieldy. (Q9)

## AD-10 · Accepted risk: evaluator sandboxes hold the setup-token

- **Decision:** evaluator sandboxes hold the owner's `setup-token`. Their egress is limited to Anthropic's API hosts, with read-only tools and spec 005's isolation flags.
- **Alternatives:** a runner-side token proxy (unverified); a packet built outside the sandbox.
- **Why:** the subject is the owner's own public repository, and the tools are read-only. The proxy is a BACKLOG "Next" item. (Q10)

## AD-11 · A read-only repository token, used before subject code runs

- **Decision:** the runner clones with a fine-grained, read-only token, in a step before any subject code runs.
- **Alternatives:** an anonymous clone; a GitHub App.
- **Why:** the user's choice. It gives one code path for public and private subjects. (Q11)

## AD-12 · A council step fails closed

- **Decision:** if any council member's job cannot run, the whole step is *not evaluated*.
- **Alternatives:** the median of a majority; retry once.
- **Why:** it is the simplest fail-closed rule, and a council keeps its declared size. (Q12)

## AD-13 · Outcome classification

- **Decision:** infrastructure causes are *not evaluated*: eviction or preemption, image pull failure, unschedulable, clone failure, service never ready, sandbox never started. A non-zero exit, a timeout, and OOMKilled during the main command are *failed*. The runner reads pod state to tell them apart.
- **Alternatives:** everything infrastructure-like *not evaluated*; decide after a prototype.
- **Why:** with OOM, the subject's code ran and exceeded the published limits. A Job-level `DeadlineExceeded` alone cannot separate the cases. (Q13)

## AD-14 · Binding acquires the lease

- **Decision:** binding a session acquires the run's lease for its user. Only the owner can take it over, in the UI. No heartbeat.
- **Alternatives:** no leases; a lease per session.
- **Why:** gives separation of duties a precise record. (Q14)

## AD-15 · Driver token scopes in the feedback loop

- **Decision:** the driver token may read cross-run analysis (from the analysis service, AD-24) and submit draft definitions (to the engine). It never publishes.
- **Alternatives:** read only; a separate token.
- **Why:** publishing stays a human UI action (Art. 2). (Q15)

## AD-16 · Idempotency, serialization, and dispatch

- **Decision:** every mutating driver call and every human decision takes an idempotency key. Each request locks the run's row. Runner dispatch goes through a transactional outbox, and Kubernetes Jobs are named after the engine's job ID.
- **Alternatives:** keys on transitions only; defer to spike 9.
- **Why:** retries are safe end to end. (Q16)

## AD-17 · Shadow typed decisions run in parallel

- **Decision:** the shadow providers (Jev only in the MVP, AD-28) are called when the step starts, in parallel with the council, each with its own deadline, and are never awaited.
- **Alternatives:** call them after the council.
- **Why:** shadow mode never delays a checkpoint. (Q17)

## AD-18 · Runner logs: a bounded tail

- **Decision:** the job result carries a log tail of about 64 KB, which the engine stores with the step result.
- **Alternatives:** full logs; logs kept by the runner.
- **Why:** enough provenance, with no log store. (Q18)

## AD-19 · No notifications outside the UI

- **Decision:** the UI inbox is the only channel in the MVP.
- **Alternatives:** email; Slack.
- **Why:** happy path first. (Q19)

## AD-20 · Neutrality by module rules

- **Decision:** module dependency rules, checked in the build, keep core modules free of edges, adapters, and SDLC, git, and Claude Code names. The toy workflow test stays "Next".
- **Alternatives:** separate deployables; a toy workflow test now.
- **Why:** follows from AD-1. Spike 9 picks the rule checker. (G7)

## AD-21 · A separate MCP gateway

- **Decision:** the MCP server is its own Deployment, a stateless gateway. It translates MCP tool calls into calls to the engine's public HTTP driver API, the same API the hooks call (and analysis read tools into calls to the analysis API, AD-24), and forwards the caller's driver token unchanged. It holds no state and no credential of its own. When the engine is unreachable, it returns an error. This refines AD-1.
- **Alternatives:** MCP as a module in the engine (AD-1 as first drafted).
- **Why:** it gives the engine one provider-neutral driver contract. MCP is changing quickly (2025-11-25 stateful, 2026-07-28 stateless, Java SDK 2.1 and 2.2), and those changes then touch only the gateway, not engine releases. Art. 1 and fail closed still hold, because the gateway decides nothing. Raised by the user after the recommendation was agreed. (Q20)

## AD-22 · Configuration split by kind

- **Decision:** configuration is split by kind.
  - Workflow configuration is in the database, owned by `definitions`.
  - Identity and access (workspaces, users, roles, human accounts, hashed driver tokens) is in the database, owned by a new core module, `identity`.
  - Operational settings (runner address, evaluator default timeout, deadline margin, poll interval, provider endpoints, Jev model version) are in a Kubernetes ConfigMap read at startup, owned by the module that uses each one.
  - Secrets are in Kubernetes Secrets.

  Operational values that affect an outcome are recorded on the result or event.
- **Alternatives:** everything but secrets in the database, as INTENT Persistence says.
- **Why:** it is the simplest option for one operator with no settings UI, and replay still holds because outcome-affecting values are recorded. Storing operational settings in the database and editing them in the UI is BACKLOG "Next". (Q21)

## AD-23 · One relational database, with document columns

- **Decision:** one relational database, with ACID transactions across tables, row-level locks, and unique constraints. Document-shaped data goes in document columns:
  - a definition version stores its YAML exactly as approved, plus a parsed document;
  - event and evidence payloads are documents.

  Everything else is ordinary tables. The product (PostgreSQL is the obvious candidate) is chosen in spike 9.
- **Alternatives:** MongoDB for everything; MongoDB for definitions plus a relational database for run state.
- **Why:**
  - Definitions are small (the reference workflow is about 8 KB), immutable once published, and read whole by (workflow, version), so a document column is enough.
  - Run history is many related records: one transaction across state, events, idempotency, and the outbox (AD-5, AD-16); a lock per run; uniqueness rules; and joins and aggregates across runs for the quality view and the feedback loop. Cross-run analysis is where MongoDB is weakest.
  - Splitting the data across two systems would lose the single transaction when a version is published or pinned to a run.

  Raised by the user. (Q22)

## AD-24 · A separate analysis service, fed by a broker

- **Decision:** analysis (the quality view, cross-run measures, weakest-step detection) is its own Deployment.
  - **Feed:** the engine writes events, and telemetry rows once joined to runs, to its outbox in the same transaction. The `scheduler` drains them through `DueWork` (AD-25) and publishes them to a message broker topic keyed by run ID.
  - **Store:** the analysis service subscribes, deduplicates by event ID, and keeps its own derived store (a separate schema in the MVP, with separate credentials). It stores its progress in the same transaction as each projection update.
  - **Rebuilds:** the engine offers a paged event read, since broker retention is limited.
  - **Auth:** analysis checks credentials by introspection against the engine and accepts no writes. Run scores stay in the engine.
  - **Broker:** sits behind an engine port. The product is chosen in spikes 9 and 10 (Pub/Sub on GKE; an emulator or a self-hosted broker locally).
- **Alternatives:**
  - analysis as an engine module (AD-1 as first drafted);
  - a cursor feed that analysis long-polls (proposed first in the spike);
  - read-only access to the engine's tables.
- **Why:** analysis only reads and changes on its own cadence. A durable broker with an outbox ensures delivery without burdening the engine (the user's point). It also keeps two rules: only the engine accesses its persistence, and only the engine computes scores (Art. 1). The Pub/Sub details are still to be checked against current docs in spike 9. (Q23)

## AD-25 · Inbound ports and a scheduler adapter

- **Decision:** the engine is hexagonal on both sides.
  - The core offers five inbound ports, the use cases it exposes: `DriverOperations`, `HumanOperations`, `TelemetryIntake`, `EventFeed`, and `DueWork`.
  - The inbound adapters (`driver-api`, `ui-api`, `telemetry-ingestion`, `event-read`, and a new `scheduler`) depend only on those ports.
  - Each port call is one use case and one transaction.
  - The timer moves out of the core into the `scheduler`, which calls `DueWork`. The core module `outbox + deadlines` keeps the rules and runs no threads.
- **Alternatives:** inbound adapters call core modules directly; the timer stays inside the core.
- **Why:**
  - One surface serves every transport, so a CLI or MCP in process is just another adapter.
  - The transaction and idempotency boundary sits in one place.
  - Module rules can enforce that adapters never reach core internals.
  - Use cases can be tested without HTTP.

  Raised by the user. (Q24)

## AD-26 · Kubernetes Jobs for sandboxes; no Cloud Tasks

- **Decision:**
  - Sandboxes stay Kubernetes Jobs, the same on the local cluster and on GKE.
  - On GKE, the target is GKE Sandbox (gVisor RuntimeClass), with limits on every container and the metadata server blocked.
  - Google Cloud Tasks is not used.
  - Cloud Run Jobs stay a possible later runner adapter.
- **Alternatives:** Cloud Tasks as the outbox or the dispatch channel; Cloud Run functions as the sandbox; Cloud Run Jobs as the sandbox now.
- **Why:**
  - Cloud Tasks is an HTTP delivery queue (at least once, 30-minute maximum dispatch deadline, no official emulator). It does not run code, so it cannot be the sandbox, and as a dispatch channel it would only duplicate the outbox (AD-16).
  - GKE Sandbox adds kernel-level isolation for untrusted subject code without changing the Jobs.
  - Cloud Tasks cannot be the outbox, because enqueueing cannot join the database transaction. It may later be an optional delivery leg after the outbox, behind `RunnerPort`.
  - Cloud Run functions are built from source rather than running a declared image, reuse instances across requests (not throwaway), document no sidecars, and cap HTTP calls at 60 minutes.
  - Cloud Run Jobs offer microVM isolation but have no local equivalent, and egress control for jobs is only partly documented.

  Checked against Google's docs on 2026-10-21. Raised by the user. (Q25)

## AD-27 · Git is the evidence channel

- **Decision:** content reaches the system only through git.
  - The reference workflow needs no driver-submitted evidence. A transition request carries only the revision.
  - The runner reads the subject at that revision for checks, evaluators, and typed-decision input packets.
  - `diff_present`, `requirements_present`, and `merge_recorded` are runner `command` checks. Requirements are a committed file at a path the definition declares.
  - The UI shows the change through a subject view the definition declares, read in the browser from the git host at the reviewed revision, with the human's own login. In the MVP that is a link to the compare page.
  - Jev's input comes from a runner input packet job that returns bounded text, which the engine sends to Jev.
  - `submit_evidence` stays in the engine for workflows that do not use git.
- **Alternatives:**
  - driver-submitted `diff`, `requirements_doc`, `local_test_summary`, and `merge_receipt` (spike 003);
  - a UI backend, or the engine, reading git;
  - the runner calling Jev itself.
- **Why:**
  - One source of truth for content.
  - Checks become engine-verified instead of driver-reported.
  - No new server credentials.
  - Only the engine calls models and holds the Jev key (Art. 4), and the runner still never calls the engine (AD-2).

  Raised by the user. (Q26)

## AD-28 · Jev is the only decision provider in the MVP

- **Decision:** Laya is not in the MVP. `laya-serve` is not deployed, and the `systemone` adapter talks to Jev only. Laya stays a later provider behind the same DecisionEngine port, and its fine-tuning stays in BACKLOG "Next".
- **Alternatives:** Laya and Jev side by side (spike 007).
- **Why:** Laya needs training to be useful (zero-shot it was at chance in spike 007), which is too costly now. Decided by the user. (Q27)

## AD-29 · Typed-decision trees in the MVP

- **Decision:** a typed decision can be a decision tree, declared in the definition.
  - **Branches:** `then` branches, each with a `when` condition (`label_in`, `min_confidence`, `max_confidence`) and either `ask` for a follow-up question or `conclude` with a named conclusion. The first matching branch wins.
  - **Limits:** at most 3 questions on a path and 5 per tree.
  - **Ownership:** the tree definition belongs to the core `definitions` module, as part of each definition version: validated there (rules 12, 17, 18), versioned, approved by a human, and pinned per run.
  - **Walk:** a core `typed-decisions` module walks it, one question per DecisionEngine call, with one runner input packet per tree. Every node, answer, branch, and conclusion is recorded.
  - **Mode:** shadow only; conclusions change nothing in the run.
  - **First tree:** `code_quality_band` → `has_defect` → `likely_defect`, from spike 007's battery.
- **Alternatives:**
  - the tree inside the DecisionEngine adapter;
  - trees in BACKLOG "Next";
  - branches that act on the run.
- **Why:**
  - Which question comes next is workflow knowledge, so it stays in the definition (versioned, approved by a human, open to the feedback loop).
  - The port stays provider-neutral.
  - Every node can be replayed and measured.
  - Acting on the run would need the `advisory` or `bounded_control` modes, which are earned on measured evidence, and humans still decide human steps (Art. 2).

  Raised by the user. (Q28)
