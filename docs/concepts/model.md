# Domain model

From spike 001 (`specs/001-concepts-what-a-workflow-is/`), where every point is decided in the Log. Terms are defined in `glossary.md`.

## Entities and relationships

Definition side: what a published definition version contains.

```mermaid
erDiagram
  WORKSPACE ||--o{ WORKFLOW : holds
  WORKFLOW ||--|{ DEFINITION_VERSION : "has versions (one current)"
  DEFINITION_VERSION ||--|{ STAGE : declares
  DEFINITION_VERSION ||--|{ TRANSITION : declares
  DEFINITION_VERSION ||--o{ EVIDENCE_TYPE : declares
  DEFINITION_VERSION ||--o{ RUN_INPUT : declares
  DEFINITION_VERSION ||--o{ PROTECTED_ACTION : declares
  DEFINITION_VERSION ||--o{ TYPED_QUESTION : declares
  STAGE ||--o| STAGE_TASK : "asks for"
  STAGE_TASK }o--o{ EVIDENCE_TYPE : "tests produce"
  STAGE ||--o{ TRANSITION : "is source of"
  STAGE ||--o{ TRANSITION : "is target of"
  TRANSITION ||--o| CHECKPOINT : "guarded by"
  CHECKPOINT ||--|{ STEP : "ordered steps"
  STEP }o--o{ EVIDENCE_TYPE : "inputs (min trust)"
  STEP }o--o| TYPED_QUESTION : "uses (with mode)"
  PROTECTED_ACTION }o--|{ STAGE : "allowed in"
```

Run side: what the engine creates while a run executes.

```mermaid
erDiagram
  DEFINITION_VERSION ||--o{ RUN : "pins"
  USER ||--o{ RUN : owns
  RUN ||--|{ STAGE_VISIT : "visits (in order)"
  STAGE_VISIT ||--o{ EVIDENCE : collects
  STAGE_VISIT ||--o{ ITERATION : "records"
  STAGE_VISIT ||--o{ ESCALATION : "on exhaustion"
  RUN ||--o{ TRANSITION_REQUEST : receives
  TRANSITION_REQUEST ||--o| CHECKPOINT_ATTEMPT : "runs (if guarded)"
  CHECKPOINT_ATTEMPT ||--|{ STEP_RESULT : "one per step"
  STEP_RESULT ||--o{ EVALUATION_TASK : "agent evaluator (council of N)"
  STEP_RESULT ||--o| HUMAN_STEP_REQUEST : "human executor"
  STEP_RESULT ||--o{ DECISION_RECORD : "DecisionEngine (any mode)"
  STEP_RESULT }o--o{ EVIDENCE : "used"
  RUN ||--o{ DRIVER_SESSION : "bound to"
  DRIVER_SESSION ||--o{ LEASE : holds
  USER ||--o{ DRIVER_SESSION : "drives"
  RUN ||--o{ PROTECTED_ACTION_CHECK : "authorizations"
  RUN ||--|{ EVENT : "append-only log"
```

Rules the diagrams cannot show:

- A run pins the definition version that was current when it started. Later versions never change it.
- At most one active run exists per workflow and subject key.
- A run has exactly one current stage, which is the stage of its latest stage visit.
- A run has at most one active lease.
- A run has at most one open checkpoint attempt. While it is open, the driver waits: it cannot withdraw the attempt or request another transition. It can still submit evidence. The open attempt ignores it unless its revision matches; it is there for the next request.
- A transition with no checkpoint commits as soon as it is requested and validated.
- A step reads evidence of its input types from the latest visit of the stage where that type was submitted. Evidence is validated at decision time against the revision in the transition request.

## Lifecycles

### Stage visit and its stage task

```mermaid
stateDiagram-v2
  state "criteria met" as met
  [*] --> working : stage entered (spec from status)
  working --> working : iteration recorded, completion check failed
  working --> met : completion check passes
  working --> paused : usage limit, interrupt, or error
  paused --> working : driver resumes
  working --> exhausted : a limit reached
  exhausted --> working : owner extends the budget in the UI
  exhausted --> [*] : owner directs a rework, or abandons the run
  met --> [*] : driver requests a transition (its checkpoint runs)
```

Iteration limits are enforced by the driver kit and recorded by the engine, so an overrun is visible. Wall time per stage visit the engine measures from its own timestamps. Only exhaustion escalates; paused work does not. Iterating is how the Claude Code kit is expected to do the work (a goal loop driven by a Stop hook), not something the model requires.

### Run

```mermaid
stateDiagram-v2
  [*] --> active : start_run (current version, subject key free)
  active --> active : transition accepted, failed attempt, or rework
  active --> completed : enters a success terminal stage
  active --> failed : enters a failure terminal stage
  active --> abandoned : owner abandons in the UI (driver may request)
  completed --> [*]
  failed --> [*]
  abandoned --> [*]
```

Score classification is separate from status and derived from it:

| Run status | Score classification |
| --- | --- |
| `completed` with no override | `scored` |
| `completed` with an override | `overridden` |
| `failed` or `abandoned` | `excluded`, with the partial score shown separately |

### Definition version

```mermaid
stateDiagram-v2
  [*] --> draft : loaded from a file or proposed by the feedback loop
  draft --> draft : edited
  draft --> published : human approves in the UI (becomes current)
  draft --> rejected : human rejects in the UI
  published --> superseded : another version is published
  rejected --> [*]
  superseded --> [*]
```

Revert publishes a copy of an older version as a new draft, which then follows the same path.

### Transition request and checkpoint attempt

```mermaid
stateDiagram-v2
  [*] --> requested : request_transition (revision, idempotency key)
  requested --> denied : not permitted now (wrong stage, stale revision, an attempt already open)
  requested --> accepted : transition has no checkpoint
  requested --> running : checkpoint attempt starts
  running --> running : next step (steps strictly in order)
  running --> passed : every step has a result and no required step failed
  running --> failed : a required step failed (rest are not evaluated)
  running --> cancelled : the run is abandoned
  passed --> accepted : transition committed with the stage change
  failed --> rejected : failed steps and the rework path returned
  denied --> [*]
  accepted --> [*]
  rejected --> [*]
  cancelled --> [*]
```

A failed attempt leaves the run in its stage; the driver can request again. The driver cannot withdraw an open attempt; only abandoning the run cancels it. Each new attempt re-runs every step. Each failed attempt of a required step earns its deduction.

### Step result

```mermaid
stateDiagram-v2
  state "not evaluated" as not_evaluated
  [*] --> pending : attempt reaches this step
  [*] --> not_evaluated : an earlier required step failed
  pending --> passed : pass condition met
  pending --> failed : pass condition not met
  pending --> not_evaluated : executor unavailable (fail closed) or attempt cancelled
  failed --> overridden : human overrides in the UI (required steps), attempt resumes at the next step
```

`not evaluated` is never `passed`. An override keeps the raw result and flags the run score.

### Evaluation task and human step request

```mermaid
stateDiagram-v2
  state "Evaluation task" as ET {
    [*] --> issued : step reached (N tasks for a council of N)
    issued --> submitted : evaluator submits with the task ID
    issued --> cancelled : attempt ends first
  }
  state "Human step request" as HS {
    state "rejected" as hs_rejected
    state "cancelled" as hs_cancelled
    [*] --> waiting : step reached, queued in the UI inbox
    waiting --> approved : authorized human approves
    waiting --> hs_rejected : authorized human rejects
    waiting --> hs_cancelled : attempt ends first
  }
```

When an attempt is cancelled (the run is abandoned), its open evaluation tasks and human step requests become `cancelled`, and late submissions are rejected. Nothing waits in the UI inbox for a dead attempt. Timeouts and retries of evaluation tasks, and human steps scored on a rubric band, are in BACKLOG "Next".

## What the engine knows versus what definitions declare

| The engine knows (generic) | Only definitions declare (content) |
| --- | --- |
| Workflow, definition version, stage, transition, rework flag, checkpoint, step, run, stage visit | Stage names, the graph, which transitions are rework |
| Stage task template, iterations, limits (iterations, wall time, consecutive failed checks), exhaustion escalation | Each stage's task, acceptance criteria, tests, and limit values |
| Executor kinds, step roles, pass conditions, scales, councils, deductions | Rubric text, bands, points, thresholds |
| Evidence, trust levels, opaque revision, provenance | Evidence type names (`diff`, `test_summary`, `fact_check_report`) |
| Run inputs as opaque data, subject key | Which inputs exist (repository, branch, document ID) |
| Protected action, allowed stages, authorization | Action names (`merge`, `publish`) |
| Typed question, labels, modes, DecisionEngine | Question wording and label sets |
| Driver capabilities, sessions, leases, actor types, events | Which capabilities a workflow requires |

SDLC terms found outside definitions, and their neutral form:

- "Max turns": a provider term. Definitions use *iterations*; each driver kit maps them to its own turns or hooks.

- "One run per branch" (BACKLOG, CLAUDE.md): one active run per workflow and subject key; the SDLC kit uses repository + branch as the subject.
- Engine-verified evidence by "resolving a commit on the shared remote" (intent doc, Evidence and trust): post-MVP, through a verifier adapter outside the engine core. In the core, a revision is an opaque string.

## Toy workflow: article publication

A deliberately non-SDLC workflow, sketched on paper to check that every concept fits without SDLC terms.

- **Subject key:** the article ID. **Run inputs:** article ID, title, target section.
- **Revision:** the document's revision number in the editing tool.
- **Driver:** a writing agent working on the article.

```mermaid
stateDiagram-v2
  direction LR
  [*] --> drafting
  drafting --> fact_check : stage check, word count within limits
  fact_check --> editorial_review : major checkpoint (see below)
  fact_check --> drafting : rework
  editorial_review --> ready : checkpoint, human editor approves with separation of duties
  editorial_review --> drafting : rework
  ready --> published : publication_receipt exists
  drafting --> withdrawn
  published --> [*] : success terminal
  withdrawn --> [*] : failure terminal
```

Stage task of `drafting`, as configured by this workflow:

- **Task:** "Write the article {{title}} for section {{target_section}}."
- **Acceptance criteria:** "Every claim has a source in the source list; length within the section's limits."
- **Tests:** evidence `source_list` and `word_count` produced by the stage task.
- **Limits:** 8 iterations, 2 hours per visit, 3 consecutive failed completion checks.

The major checkpoint `fact_check → editorial_review`:

1. Required, deterministic: evidence `source_list` exists and has at least 3 entries (minimum trust: driver-reported).
2. Required, agent evaluator: "claims supported by sources", scale 1–10 with a rubric, pass at 6, council of 3.
3. Optional, agent evaluator: "readability", scale 1–5 with a rubric.
4. Shadow, Laya: typed question "publication risk" with labels `low`, `medium`, `high`, alongside step 2.

Protected action `publish` is allowed only in stage `ready`. The driver's hook calls `authorize_action` with the current revision. After publishing, the driver submits `publication_receipt` and requests `ready → published`.

**Result:** every concept maps without an SDLC term. The engine needed nothing beyond the generic vocabulary in the table above.
