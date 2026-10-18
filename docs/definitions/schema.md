# Definition schema (draft v0)

From spike 003 (`specs/003-definition-model-draft/`), where each choice is decided in the Log. Terms are defined in `docs/concepts/glossary.md`. The reference workflow written in this schema is `sdlc-reference.yaml`, and a hand-computed run is in `example-run.md`.

> **Being revised.** This draft predates the runner added by the 2026-10-18 intent review. Where steps run, the runner check type, and trust levels are reopened in spike 003 (G7).

This is a draft for milestone 1. The formal JSON Schema file and the validator are written there, with the library chosen in spike 9.

## Format and validation

- A definition version is one self-contained YAML file.
- It is validated in two layers:
  1. **Structure:** a published JSON Schema (draft 2020-12). Editors and the feedback-loop skill can use it to check a draft before submitting it. Unknown keys are rejected.
  2. **Semantics:** checks in the engine for what JSON Schema cannot express, listed under "Semantic rules" below.
- A definition that fails either layer is rejected and never becomes a draft.
- The file names its format in `schema` (now `ai-workflow/v0`) and its workflow in `workflow`. It never carries its own version number: the engine assigns sequential integers when a human publishes a version.
- All label and ID values are strings. JSON Schema types them as strings, so an unquoted YAML 1.1 boolean such as `no` is rejected rather than silently read as `false`.

## Top-level keys

| Key | Required | Content |
| --- | --- | --- |
| `schema` | yes | Format version, `ai-workflow/v0`. |
| `workflow` | yes | Stable workflow ID, such as `sdlc-reference`. |
| `title`, `description` | `title` yes | Text for humans. |
| `required_capabilities` | no | Driver capabilities a session must declare: `core`, `activity_recording`, `evaluation`, `enforcement`, `telemetry`. |
| `run_inputs` | no | Map of input name to `{ description }`. Stored by the engine without interpretation. |
| `evidence_types` | yes | Map of type name to `{ description, schema? }`. `schema` is an optional embedded JSON Schema for the payload. |
| `typed_questions` | no | Map of question ID to `{ prompt, labels, inputs }`. |
| `protected_actions` | no | Map of action name to `{ description, allowed_in: [stage, ...] }`. |
| `scoring` | yes | `deductions: { failed_attempt, rework_loop }`, in points of the normalized 0–100 score. |
| `stages` | yes | Map of stage ID to a stage. |
| `transitions` | yes | List of transitions. |

## Stages

A stage is either non-terminal, with a stage task, or terminal.

```yaml
implementation:
  entry: true            # exactly one stage has this
  task:
    prompt: "Implement on branch {{inputs.branch}}: {{evidence.requirements_doc.summary}}"
    acceptance_criteria: [ ... ]
    produces: [diff]     # the stage task's tests, as evidence types
    limits: { iterations: 12, wall_time: 3h, failed_checks: 3 }
complete:
  terminal: success      # or failure
```

- **Templates.** `prompt`, `acceptance_criteria`, and a human step's `prompt` may contain placeholders: `{{inputs.<name>}}` and `{{evidence.<type>.<field path>}}`. Substitution only: no conditions, loops, or functions. Evidence is read from the latest visit of the stage that produced it.
- **Limits** are required on every stage task: `iterations` (positive integer), `wall_time` (a duration such as `30m` or `3h`), and `failed_checks` (consecutive failed completion checks). There are no workflow-level defaults.

## Transitions

```yaml
- id: rework
  from: verification
  to: implementation
  rework: true           # each traversal costs the rework_loop deduction
  checkpoint: { ... }    # optional; at most one
```

A checkpoint has an optional `label` (such as `major` or `stage check`, display only) and an ordered list of `steps`.

## Steps

Common keys: `id`, `executor`, `role` (`required` or `optional`), `points`, and `inputs` (evidence types). The executor decides the rest.

| Executor | Keys | Pass condition | Points earned |
| --- | --- | --- | --- |
| `deterministic` | `check` (exactly one) | The check holds | Full points on pass, 0 on fail |
| `agent_evaluator` | `scale: [min, max]`, `rubric`, `pass: { min_score }` (required steps only), `council` (odd, default 1), `instructions` (optional), `typed_decisions` (optional) | Median raw score ≥ `min_score` | `points × (raw − min) / (max − min)` |
| `human` | `prompt`, `separation_of_duties` (default false) | The human approves | Full points on approval, 0 on rejection |

**Check types** for deterministic steps. A field is addressed as `<evidence type>.<field path>`, with dots between nested fields.

| Check | Form | Holds when |
| --- | --- | --- |
| `evidence_exists` | `{ evidence_exists: <type> }` | Evidence of that type exists at the request's revision |
| `field_compare` | `{ field_compare: { field, op, value } }` with `op` one of `eq`, `ne`, `lt`, `lte`, `gt`, `gte` | The field compares true to the literal |
| `min_items` | `{ min_items: { field, min } }` | The list field has at least `min` items |

Several conditions mean several steps. There is no expression language.

**Rubric bands** each have an `id`, a `range: [low, high]`, and a `descriptor`, with optional `examples`. Band IDs are the labels a shadowing typed question uses.

**Typed decisions** attach to the step they shadow, not to the ordered list:

```yaml
typed_decisions:
  - question: code_quality_band
    provider: laya
    mode: shadow
```

They run when that step runs, earn no points, and never delay or block the checkpoint. A provider failure records *not evaluated* for the decision only.

**Not in the MVP schema:** step-level deductions, trust levels other than `driver_reported` (so there is no `min_trust` key yet), human steps scored on a band, and the `advisory` and `bounded_control` modes.

## Scoring rules

- A step on the path the run took counts its latest evaluation. Steps on transitions the run never took do not count toward possible points.
- Run score = earned points ÷ possible points × 100, minus deductions, never below 0.
- Deductions, as separate line items:
  - `failed_attempt` for each checkpoint attempt in which a required step failed, including a failure that a human then overrode.
  - `rework_loop` for each traversal of a `rework` edge. A failed attempt followed by rework pays both.
- An overridden step keeps its raw result and earns the points mapped from it, which is 0 for a pass/fail step. The run is classified `overridden`.
- Values are computed and stored unrounded and displayed to one decimal.

## Authorization rules the schema relies on

These are engine behavior, not definition keys, and are recorded here because the reference workflow depends on them.

- `authorize_action` succeeds only while the run is active, in a stage listed in `allowed_in`, and the request's revision equals the revision of the transition request whose checkpoint let the run into that stage. A commit made after the review needs a new checkpoint attempt.
- Separation of duties on a human step binds both the approval and any override in that checkpoint: nobody who ever held a lease on the run may approve or override.
- A human step's rejection cannot be overridden. The run reworks, retries, or is discarded instead.

## Semantic rules

The engine rejects a definition when any of these fails.

1. IDs are unique within their kind: stages, transitions, steps (across the whole definition), evidence types, typed questions, protected actions, and rubric bands within a step.
2. Exactly one stage is the entry. At least one terminal stage is `success`. Terminal stages have no task and no outgoing transitions. Every non-terminal stage has a task.
3. Every transition's `from` and `to` names a declared stage.
4. Every stage is reachable from the entry, and a success terminal is reachable from every non-terminal stage.
5. Every evidence type named in `produces`, `inputs`, a check, or a template is declared. Every field path names a field of that type's `schema`, when one is declared. `min_items` targets an array field, and a `field_compare` literal has the field's type.
6. A check's evidence type is among its step's `inputs`.
7. Every step input is produced by some stage task.
8. A rubric's bands are contiguous, do not overlap, and cover the whole scale. `pass.min_score` lies within the scale. Optional steps have no `pass`.
9. `council` is odd and at least 1, and appears only on `agent_evaluator` steps.
10. A typed decision names a declared question and the declared provider `laya`, uses mode `shadow`, and sits on an `agent_evaluator` step. Its question's labels equal that step's band IDs.
11. Every `{{inputs.x}}` names a declared run input. Every `{{evidence.t.f}}` names an evidence type produced by a stage that comes before this one on some path from the entry.
12. Every `allowed_in` names a non-terminal stage. If any protected action is declared, `required_capabilities` includes `enforcement`.
13. Limits are positive. Deductions and points are not negative.

## Invalid fixtures for milestone 1

One rule each, so the validator's error for each can be checked:

- A transition to an undeclared stage (rule 3).
- No success terminal stage (rule 2).
- A stage nobody can reach (rule 4).
- Rubric bands with a gap, such as `[1, 3]` and `[5, 10]` (rule 8).
- `pass.min_score` outside the scale (rule 8).
- A council of 2 (rule 9).
- A typed question label written as unquoted `no` (structure layer).
- A `field_compare` on a field missing from the evidence type's schema (rule 5).
