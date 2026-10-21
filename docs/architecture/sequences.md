# Happy path sequences

From spike 010. Components and rules are in `README.md`. The examples use the reference workflow (`docs/definitions/sdlc-reference.yaml`), but the engine only sees neutral concepts: stages, transitions, checkpoints, steps, evidence, and action names. Spike 011 fixed the driver API contract: operations, schemas, and a full run as calls are in `docs/driver-api/`.

MCP calls go through the MCP gateway, which forwards them with the caller's driver token to the engine's HTTP driver API (Q20). Hooks call that API directly. Every mutating call carries an idempotency key. Every request on a run locks the run's row for its transaction, and writes its state change and events together.

## 1. Start and bind

```mermaid
sequenceDiagram
  autonumber
  participant CC as Claude Code + kit
  participant G as MCP gateway
  participant E as Engine
  CC->>E: SessionStart hook: register session (driver token)
  E-->>CC: unbound, or status of the bound run (injected as context)
  CC->>G: MCP start_run(subject inputs)
  G->>E: HTTP driver API (same token)
  E->>E: derive subject key from the inputs in subject.key, refuse if an active run exists
  E->>E: create run on the current definition version, bind session, acquire lease for the user
  E-->>G: run status: stage task, permitted transitions
  G-->>CC: run status: stage task, permitted transitions
```

- An unknown session is registered lazily on its first PreToolUse (spec 005). Forks and `/clear` bind again (spec 006).
- A second run in a bound session is refused. Another user's binding is refused unless the owner takes over the lease in the UI (Q14).

## 2. Push work to git

Git is the evidence channel (Q26). The driver submits no evidence in the reference workflow. It commits, pushes, and names the revision in its next transition request. The runner reads everything else from git at that revision. `submit_evidence` stays in the driver API for workflows whose content does not live in git.

```mermaid
sequenceDiagram
  autonumber
  participant CC as Claude Code + kit
  participant GH as Git host
  participant G as MCP gateway
  participant E as Engine
  CC->>CC: write requirements or code, run tests locally
  CC->>GH: commit and push the branch (revision r)
  CC->>G: MCP request_transition(subject, transition, revision r)
  G->>E: HTTP driver API (same token)
  E->>E: record the request with revision r (checkpoint, if any: sequence 3)
  E-->>G: accepted or attempt open
  G-->>CC: accepted or attempt open
```

## 3. Request a transition and run its checkpoint

The `approve_release` transition's checkpoint, with runner checks, an evaluator council with a shadow typed decision, and a human step. Steps run strictly in order and fail fast. The engine moves the attempt forward on each runner result it polls, each deadline, and each human decision.

```mermaid
sequenceDiagram
  autonumber
  participant CC as Claude Code + kit
  participant G as MCP gateway
  participant E as Engine
  participant R as Runner
  participant S as Sandbox (Job)
  participant D as Jev
  participant H as Human (UI)
  CC->>G: MCP request_transition(subject, transition, revision)
  G->>E: HTTP driver API (same token)
  E->>E: open attempt (one per run), outbox: runner job for the check step, set deadline
  E-->>G: attempt open, checkpoint_status
  G-->>CC: attempt open, checkpoint_status
  E->>R: dispatch(job ID, command packet)  [scheduler → DueWork]
  R->>S: create Job named after job ID (clone step, then command)
  loop until final or deadline
    E->>R: get(job ID)
    R-->>E: running
  end
  S-->>R: exit 0
  E->>R: get(job ID)
  R-->>E: passed + provenance + log tail
  E->>E: record step passed, outbox: evaluator jobs for the council step, set deadlines
  par council
    E->>R: dispatch(evaluator job × council size)
    R->>S: headless Claude Code, read-only tools
  and shadow decision tree (never awaited, Q28)
    E->>R: dispatch(input packet job: requirements, diff at r)
    R->>S: clone r, resolve subject inputs, no model
    E->>R: get(job ID)
    R-->>E: bounded text packet (one per tree)
    loop each node, until no branch matches or not evaluated
      E->>D: /v1/systemone question + packet (own deadline, Jev key)
      D-->>E: label, confidence, usage
      E->>E: record node, check branches in order: ask the follow-up, conclude, or stop
    end
  end
  E->>R: get(job IDs)
  R-->>E: structured_output per member
  E->>E: median raw score vs min_score, record step result
  E->>E: next step is human: queue in the UI inbox, with the subject view at r
  CC->>G: MCP checkpoint_status
  G->>E: HTTP driver API (same token)
  E-->>G: waiting for a human
  G-->>CC: waiting for a human
  Note over CC: session ends (Q7)
  H->>H: open the subject view: the git host's compare page at r, in the browser (Q26)
  H->>E: UI API: approve (human session, idempotency key)
  E->>E: check separation of duties, record decision, attempt passes, commit transition, compute score
  Note over CC: later session starts
  CC->>E: SessionStart hook
  E-->>CC: status: new stage, last decision and its reason
```

Outcomes that change the flow:

- A step *failed*: the attempt ends, and `status` carries the reason. The driver reworks and requests the transition again.
- A step *not evaluated* (the runner could not run, a council member could not run, or a deadline passed): the attempt does not pass (fail closed), with no deduction (spec 003 Q27).
- A shadow typed decision that fails or is truncated is *not evaluated* for that decision only, and never affects the checkpoint.

## 4. Authorize a protected action

```mermaid
sequenceDiagram
  autonumber
  participant CC as Claude Code + kit hook
  participant G as MCP gateway
  participant E as Engine
  CC->>CC: PreToolUse: Bash "gh pr merge …"
  CC->>CC: kit maps tool call → action "merge", revision (Q3)
  CC->>E: hook HTTP authorize_action(session, action, revision)
  E->>E: stage in allowed_in? revision == reviewed revision? lease held?
  alt allowed
    E-->>CC: allow (event recorded)
  else denied
    E-->>CC: deny with reason (hook blocks the tool call)
  end
  CC->>G: MCP request_transition(finish, reviewed revision)
  G->>E: HTTP driver API (same token)
  E->>E: stage check merge_recorded: runner checks the base branch contains the reviewed revision (Q26)
```

The engine never sees the command. Any tool call the kit does not map passes through as ordinary activity. GitHub branch protection stays the real gate.

## 5. Correlate telemetry

```mermaid
sequenceDiagram
  autonumber
  participant CC as Claude Code
  participant C as Collector
  participant E as Engine
  CC->>C: OTLP/HTTP metrics, logs, spans (driver token)
  C->>C: delete attributes, redact secrets, map Claude Code names → neutral names (Q9)
  C->>E: forward batch (driver token)
  C->>C: write raw file
  E->>E: check token, join on session ID within binding windows, drop unbound sessions
  E->>E: store joined rows + outbox rows (one transaction), published as in sequence 8
```

Runner evaluator usage arrives in the job result, not through the collector. DecisionEngine calls are not exported through OTel: the engine records each call attempt in its own table (spike 008).

## 6. Exhaustion escalation and rework

```mermaid
sequenceDiagram
  autonumber
  participant CC as Claude Code + kit
  participant E as Engine
  participant O as Owner (UI)
  CC->>E: report iteration (stage task limit reached before acceptance criteria)
  E->>E: stage task exhausted: open escalation, write event
  E-->>CC: verdict stop: waiting for the owner
  O->>E: UI API: resolve escalation (extend | rework | abandon)
  alt rework
    E->>E: commit the rework transition (no checkpoint on the edge) (Q8)
  else extend
    E->>E: extend the stage task's limits
  else abandon
    E->>E: run ends
  end
  CC->>E: status (next session or next call)
  E-->>CC: new stage and reason
```

## 7. Propose and publish a definition version

```mermaid
sequenceDiagram
  autonumber
  participant CC as Claude Code + kit skill
  participant G as MCP gateway
  participant A as Analysis service
  participant E as Engine
  participant H as Approver (UI)
  CC->>G: MCP read analysis (weakest step, rates, agreement)
  G->>A: analysis API (same token)
  A->>E: introspect token
  E-->>A: user, scopes (read analysis)
  A-->>G: analysis
  G-->>CC: analysis
  CC->>CC: draft a YAML diff with a hypothesis
  CC->>G: MCP submit_draft(definition, diff, hypothesis) (driver token, Q15)
  G->>E: HTTP driver API (same token)
  E->>E: validate schema and semantic rules, store as draft version
  H->>E: UI API: approve draft (human session)
  E->>E: publish as new version, previous version superseded
  Note over E: new runs pin the new version, active runs keep theirs
```

## 8. Publish events to analysis

Every state change in the sequences above also writes its events, and an outbox row for each, in the same transaction (Q23).

```mermaid
sequenceDiagram
  autonumber
  participant E as Engine
  participant B as Broker
  participant A as Analysis service
  participant U as UI (quality view)
  E->>E: state change + events + outbox rows (one transaction)
  E->>B: scheduler → DueWork publishes, ordering key = run ID (at least once)
  E->>E: mark outbox rows sent
  B->>A: deliver
  A->>A: skip if event ID already applied, else update projections + store progress (one transaction)
  A->>B: acknowledge
  U->>A: analysis API: quality view (human session)
  A->>E: introspect session
  E-->>A: human, scopes
  A-->>U: trends, rates, agreement
```

Rebuilding the analysis store (a new measure, or a reset): analysis clears its projections and reads the engine's paged event read from the start, then continues from the broker. The broker keeps messages for a limited time, so it is not the source for a rebuild.

## 9. A shadow decision tree

The tree the reference workflow declares on `code_quality` (Q28). `typed-decisions` walks it inside the shadow branch of sequence 3.

```mermaid
flowchart TD
  q1{{"code_quality_band<br/>(Jev)"}}
  q2{{"has_defect<br/>(noul)"}}
  q3{{"has_defect<br/>(noul)"}}
  c1(["conclude: likely_defect"])
  c2(["conclude: likely_defect"])
  stop1(["stop"])
  stop2(["stop"])
  q1 -- "poor or weak" --> q2
  q1 -- "confidence ≤ 0.5" --> q3
  q1 -- "otherwise" --> stop1
  q2 -- "true, confidence ≥ 0.7" --> c1
  q3 -- "true, confidence ≥ 0.7" --> c2
  q2 -- "otherwise" --> stop2
  q3 -- "otherwise" --> stop2
  classDef q fill:#EBDDFB,stroke:#6741D9,color:#111
  classDef c fill:#D3F9D8,stroke:#2B8A3E,color:#111
  classDef s fill:#F8F9FA,stroke:#495057,color:#111,stroke-dasharray:4 3
  class q1,q2,q3 q
  class c1,c2 c
  class stop1,stop2 s
```

Branches are checked in order and the first match wins, so a `weak` answer with low confidence takes the first branch. Every node is recorded, and conclusions are shown in the quality view and change nothing in the run.
