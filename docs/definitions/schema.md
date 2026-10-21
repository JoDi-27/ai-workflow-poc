# Definition schema (draft v0)

From spike 003 (`specs/003-definition-model-draft/`), where each choice is decided in the Log. Terms are defined in `docs/concepts/glossary.md`. The reference workflow written in this schema is `sdlc-reference.yaml`, and a hand-computed run is in `example-run.md`.

Updated by spike 010 (`specs/010-target-architecture/`, Q26 and Q27): definitions can declare a `subject` that the runner reads from git, so a workflow like the reference one needs no driver-submitted evidence; and Jev is the only DecisionEngine provider in the MVP.

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
| `subject` | no | What the runner reads from the subject at a request's revision: `base` (a reference the runner also fetches), `inputs` (map of name to a source the runner's source adapter resolves, such as `{ file: <path> }` or `{ diff: base }`), and `view` (a link the UI shows humans). Templates are substituted; the engine interprets nothing else (spike 010, Q26). |
| `evidence_types` | no | Map of type name to `{ description, schema? }`. `schema` is an optional embedded JSON Schema for the payload. Driver-submitted evidence, for workflows whose content does not live in the subject. |
| `typed_questions` | no | Map of question ID to `{ type?, prompt, labels, inputs }`. `type` is `choice` (the default, with `labels`) or `noul` (a statement answered `true` or `false`, with no `labels`). `inputs` name subject inputs or evidence types. |
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
    prompt: "Implement docs/requirements/{{inputs.branch}}.md on branch {{inputs.branch}}."
    acceptance_criteria: [ ... ]
    produces: [ ... ]    # optional: the stage task's tests, as evidence types
    limits: { iterations: 12, wall_time: 3h, failed_checks: 3 }
complete:
  terminal: success      # or failure
```

- **Templates.** `prompt`, `acceptance_criteria`, and a human step's `prompt` may contain placeholders: `{{inputs.<name>}}` and `{{evidence.<type>.<field path>}}`. The `subject` block and a `command` check's `run` and `env` values may contain `{{inputs.<name>}}` and `{{revision}}`, the request's revision. Substitution only: no conditions, loops, or functions. Evidence is read from the latest visit of the stage that produced it.
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

Common keys: `id`, `executor`, `role` (`required` or `optional`), `points`, and `inputs` (subject inputs or evidence types). Deterministic and agent-evaluator steps must also state `runs_in`. The executor decides the rest.

| Executor | `runs_in` | Keys | Pass condition | Points earned |
| --- | --- | --- | --- | --- |
| `deterministic` | `engine` for built-in checks, `runner` for a `command` check | `check` (exactly one) | The check holds | Full points on pass, 0 on fail |
| `agent_evaluator` | `runner` (the MVP), or `driver` | `scale: [min, max]`, `rubric`, `pass: { min_score }` (required steps only), `council` (odd, default 1), `instructions` (optional), `typed_decisions` (optional) | Median raw score ≥ `min_score` | `points × (raw − min) / (max − min)` |
| `human` | none (always the UI) | `prompt`, `separation_of_duties` (default false) | The human approves | Full points on approval, 0 on rejection |

A step in the runner runs at the transition request's revision, which the runner fetches itself (with the subject's `base` when declared), in a throwaway sandbox. A runner evaluator reads its subject inputs from its own clone. A human step shows the subject's `view`, rendered at the revision the checkpoint reviews. Its result is engine-verified, with the runner, image, command, and revision as provenance. A `driver` evaluator's result is driver-reported, and a definition that uses one must require the `evaluation` capability.

**Check types** for deterministic steps. The first three are built in and run in the engine over evidence; a field is addressed as `<evidence type>.<field path>`, with dots between nested fields. `command` runs in the runner.

| Check | Form | Holds when |
| --- | --- | --- |
| `evidence_exists` | `{ evidence_exists: <type> }` | Evidence of that type exists at the request's revision |
| `field_compare` | `{ field_compare: { field, op, value } }` with `op` one of `eq`, `ne`, `lt`, `lte`, `gt`, `gte` | The field compares true to the literal |
| `min_items` | `{ min_items: { field, min } }` | The list field has at least `min` items |
| `command` | `{ command: { image, run, env?, services?, timeout } }` | The command exits with code 0 |

Several conditions mean several steps. There is no expression language.

A `command` check, in detail:

```yaml
check:
  command:
    image: "sample-crm-check@sha256:<digest>"   # inline, pinned by digest
    run: [./mvnw, -B, verify]                    # an argument list, not a shell string
    env: { SPRING_DATASOURCE_URL: "jdbc:postgresql://localhost:5432/crm" }
    services:                                    # started before the command, reached at localhost
      postgres:
        image: "postgres:18-alpine@sha256:<digest>"
        env: { POSTGRES_DB: crm }
        ready: { command: [pg_isready, -U, crm, -d, crm] }   # required for every service
    timeout: 15m                                 # the whole job, services included
```

- The runner fetches the subject at the request's revision into the sandbox and runs `run` in it. The engine substitutes templates and passes the image, command, and environment on without reading them.
- The check *fails* when the command exits non-zero or runs past `timeout`.
- The step is *not evaluated*, failing closed, when the job cannot run: the runner is unavailable, the revision cannot be fetched, the sandbox does not start, or a service never becomes ready within `timeout`.
- A `command` check has no `inputs` and produces no evidence. Its log is kept as provenance.
- `env` values are literals. Secrets are not allowed in a definition; a throwaway service's own password is not a secret.

**Rubric bands** each have an `id`, a `range: [low, high]`, and a `descriptor`, with optional `examples`. Band IDs are the labels a shadowing typed question uses.

**Typed decisions** attach to the step they shadow, not to the ordered list:

```yaml
typed_decisions:
  - question: code_quality_band
    provider: jev
    mode: shadow
```

A typed decision can be a **decision tree** (spike 010, Q28). It carries a `then` list of branches, checked in order, and the first match wins. Each branch has a `when` condition on the answer and either `ask`, a follow-up question that may have its own `then`, or `conclude`, a named conclusion:

```yaml
typed_decisions:
  - question: code_quality_band
    provider: jev
    mode: shadow
    then:
      - when: { label_in: [poor, weak] }        # the answer's label is one of these
        ask: has_defect                          # asked of the same provider, same mode
        then:
          - when: { label_in: ["true"], min_confidence: 0.7 }
            conclude: likely_defect
      - when: { max_confidence: 0.5 }            # the answer is uncertain
        ask: has_defect
```

- **Conditions** are a fixed set, combined with "and" inside one `when`: `label_in` (labels of the question just asked; `"true"` or `"false"` for `noul`), `min_confidence`, and `max_confidence`, both against the provider's answer confidence from 0 to 1. There is no expression language.
- **Effect.** If no branch matches, the tree ends at that node. An answer that is *not evaluated* (provider failure, truncation, deadline) also ends the tree there.
- **Records.** Every question asked, its answer and confidence, the branch taken, and any conclusion are recorded. One runner input packet serves the whole tree.
- **Mode.** In the MVP a tree is shadow only. Conclusions are recorded and shown in the quality view, and change nothing in the run.

They run when that step runs, earn no points, and never delay or block the checkpoint. A provider failure, or an input the provider had to truncate, records *not evaluated* for that decision only. The same question may be asked of several providers, each recorded separately (spike 007). When a question's inputs are subject inputs, the engine first dispatches a runner input packet job that returns them as bounded text, then sends that packet to the provider (spike 010, Q26). In the MVP the only provider is `jev` (spike 010, Q27).

**Not in the MVP schema:** the `laya` provider (spike 010, Q27); step-level deductions; runner checks that produce evidence, and with them a `min_trust` key (built-in checks read only driver-submitted evidence); named check environments; human steps scored on a band; and the `advisory` and `bounded_control` modes.

## Scoring rules

- A step on the path the run took counts its latest evaluation. Steps on transitions the run never took do not count toward possible points.
- Run score = earned points ÷ possible points × 100, minus deductions, never below 0.
- Deductions, as separate line items:
  - `failed_attempt` for each checkpoint attempt in which a required step failed, including a failure that a human then overrode. An attempt that did not pass only because a required step was *not evaluated* costs nothing; it is recorded and can be retried.
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
5. Every evidence type named in `produces`, `inputs`, a check, or a template is declared, and every subject input named in `inputs` is declared in `subject.inputs`. A name is not both an evidence type and a subject input. Every field path names a field of that type's `schema`, when one is declared. `min_items` targets an array field, and a `field_compare` literal has the field's type.
6. A built-in check's evidence type is among its step's `inputs`. A `command` check has no `inputs`.
7. Every evidence-type input is produced by some stage task.
8. `runs_in` is present on every deterministic and agent-evaluator step and absent on human steps. Built-in checks run in the `engine`, `command` checks in the `runner`, and agent evaluators in the `runner` or on the `driver`. If any step runs on the `driver`, `required_capabilities` includes `evaluation`.
9. Every image in a `command` check, and in its services, is pinned by digest (`@sha256:`). Every service has a `ready` command. `timeout` is a positive duration.
10. A rubric's bands are contiguous, do not overlap, and cover the whole scale. `pass.min_score` lies within the scale. Optional steps have no `pass`.
11. `council` is odd and at least 1, and appears only on `agent_evaluator` steps.
12. A typed decision names a declared question and a provider (in the MVP only `jev`), uses mode `shadow`, appears at most once per question and provider on a step, and sits on an `agent_evaluator` step. Its root question's labels equal that step's band IDs; follow-up questions in its tree may have any labels.
13. Every `{{inputs.x}}` names a declared run input. `{{revision}}` appears only in the `subject` block and in `command` checks. Every `{{evidence.t.f}}` names an evidence type produced by a stage that comes before this one on some path from the entry.
14. Every `allowed_in` names a non-terminal stage. If any protected action is declared, `required_capabilities` includes `enforcement`.
15. Limits are positive. Deductions and points are not negative.
16. A `subject` source is either `{ file: <path> }` or `{ diff: base }`, and `{ diff: base }` requires `subject.base` (spike 010).
17. In a decision tree, every `ask` names a declared question, and no question appears twice on one path from the root. A path holds at most 3 questions, and a tree at most 5. Each branch has a `when` and exactly one of `ask` or `conclude`, and only an `ask` branch may have `then`. `label_in` values are labels of the question whose answer is tested (`"true"` or `"false"` for `noul`). Confidences lie between 0 and 1, and `min_confidence` ≤ `max_confidence` when both appear. Conclusion IDs are strings (spike 010, Q28).
18. A `noul` question has no `labels`, and a `choice` question has at least two.

## Invalid fixtures for milestone 1

One rule each, so the validator's error for each can be checked:

- A transition to an undeclared stage (rule 3).
- No success terminal stage (rule 2).
- A stage nobody can reach (rule 4).
- Rubric bands with a gap, such as `[1, 3]` and `[5, 10]` (rule 10).
- `pass.min_score` outside the scale (rule 10).
- A council of 2 (rule 11).
- A typed question label written as unquoted `no` (structure layer).
- A `field_compare` on a field missing from the evidence type's schema (rule 5).
- A `field_compare` step with `runs_in: runner` (rule 8).
- A `command` image given by tag only, such as `postgres:18-alpine` (rule 9).
- A decision tree path that asks the same question twice (rule 17).
