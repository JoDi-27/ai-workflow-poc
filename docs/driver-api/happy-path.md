# Happy path as calls: test-bed task T01

From spike 011. Test-bed task T01 (`docs/test-bed/README.md`) is "Add `GET /contacts/count`, optionally filtered by company". It passes every checkpoint on the first attempt. It runs on the reference workflow (`docs/definitions/sdlc-reference.yaml`) in `JoDi-27/sample-crm`, on branch `task/T01/v1` from base `main`. The contract is in `README.md`.

Every call carries the driver token. Responses are trimmed to the fields that matter at that point. `→` marks a hook or the agent calling the engine; `⇢` marks something that happens without the driver.

## Session 1: requirements to the release approval

1. **SessionStart** → `POST /v1/sessions`
   `{session_id: "s1", source: "startup", run_inputs: {repository: "JoDi-27/sample-crm", branch: "task/T01/v1"}, driver: {provider: "claude-code", kit_version: "0.1.0", contract_version: "v1", capabilities: [core, activity_recording, enforcement, telemetry], …}}`
   ← `{session: {bound_run_id: null}, summary: "No run on this subject. Startable: sdlc-reference (inputs: repository, branch, base, task). Next: start_run."}`
   The kit injects `summary` as context.
2. The user asks for T01. The agent calls **`status`**. The hook gates the call (`POST …/s1/gate`, `{tool_use_id}` ← `allow`) and injects `session_id: "s1"`.
   ← `startable_workflows: [{workflow: "sdlc-reference", run_inputs: [repository*, branch*, base, task]}]` (`*` = part of the subject key), `next: {action: start_run}`.
3. **`start_run`** `{workflow: "sdlc-reference", run_inputs: {repository: "JoDi-27/sample-crm", branch: "task/T01/v1", base: "main", task: "Add GET /contacts/count, optionally filtered by company"}}`
   The engine derives the subject key from `repository` and `branch`, creates run `r1` on the current version, binds `s1`, and takes the lease.
   ← Status: `stage: requirements`, the rendered task and acceptance criteria, `permitted_transitions: [{id: start_implementation, attempts: 0, checkpoint: {label: "stage check", steps: [requirements_present]}}]`, `next: {action: do_stage_task}`.
4. The agent writes `docs/requirements/task/T01/v1.md`. Each tool call is gated (`allow`) and reported as activity (`POST …/s1/activity`, async, `202`).
5. The agent commits and pushes revision `a1`.
6. **Stop** → `POST …/s1/iterations` `{self_check: {criteria_met: true, unmet: []}}`
   ← `{verdict: continue, reason: "Acceptance criteria met: request start_implementation with the pushed revision.", used: {iterations: 1, …}}`
7. **`request_transition`** `{run_id: "r1", transition: "start_implementation", revision: "a1", expected_attempts: 0}`
   ← `{result: attempt_open, attempt_id: "at1"}`
   ⇢ The runner runs `requirements_present` at `a1` and it passes. The engine commits the transition.
8. **`status`** ← `stage: implementation`, `last_decision: {kind: transition_committed}`, `permitted_transitions: [{id: verify, checkpoint: null}, …]`.
9. The agent implements the endpoint and its tests, runs them locally, commits, and pushes `b1`. Stops along the way report iterations; each self-check with unmet criteria gets `continue` and counts one failed check.
10. **`request_transition`** `{transition: "verify", revision: "b1", expected_attempts: 0}` ← `{result: committed}`, `stage: verification`.
11. The agent runs the full test suite locally, then pushes; the revision is still `b1`.
12. **`request_transition`** `{transition: "approve_release", revision: "b1", expected_attempts: 0}`
    ← `{result: attempt_open, attempt_id: "at2", status: {open_attempt: {waiting_on: runner, steps: [diff_present: running, tests_green: pending, code_quality: pending, docs_updated: pending, release_approval: pending]}}}`
    ⇢ The runner runs `diff_present` and `tests_green`. The three `code_quality` evaluators and the `docs_updated` evaluator each get an evaluation task packet and diff their own clone at `b1` against `main`. The Jev shadow decision tree runs and is never awaited. All steps pass. `release_approval` is queued in the owner's UI inbox.
13. **`status`** ← `open_attempt: {waiting_on: human}`, `pending: {human_steps: [{attempt_id: "at2", step_id: "release_approval"}]}`, `next: {action: wait, hint: "A human must decide release_approval. End the session; the next one resumes from status."}`
14. **Stop** → `POST …/s1/iterations` ← `{verdict: stop, reason: "Waiting on a human step."}`. The session ends.

⇢ The approver opens the subject view (the compare page at `b1`) in the browser and approves in the UI. The engine checks separation of duties, records the decision, passes the attempt, commits `approve_release`, and records `b1` as the reviewed revision.

## Session 2: release

15. **SessionStart** → `POST /v1/sessions` `{session_id: "s2", source: "startup", run_inputs: {repository: "JoDi-27/sample-crm", branch: "task/T01/v1"}, …}`
    The engine finds active run `r1` on that subject key, checks the lease and the capabilities, and binds `s2`.
    ← `summary: "Run r1, stage release. Last: approve_release committed by a human. Task: Merge task/T01/v1 into main with a merge commit. Protected action merge is allowed for revision b1. Next: do_stage_task."`
16. The agent runs `gh pr merge … --merge`. The PreToolUse hook maps it to the protected action:
    → `POST …/s2/gate` `{tool_use_id: "tu9", action: {name: "merge", revision: "b1"}}`
    The engine checks that `release` is in `allowed_in`, that `b1` is the reviewed revision, and that the user holds the lease.
    ← `{decision: allow}`. The merge runs; GitHub branch protection stays the real gate.
17. **`request_transition`** `{transition: "finish", revision: "b1", expected_attempts: 0}` ← `{result: attempt_open}`
    ⇢ The runner runs `merge_recorded` (`main` contains `b1`). It passes, `finish` is committed, the run reaches `complete`, and the engine computes the score.
18. **`status`** ← `run: {state: completed}`, `next: {action: none}`. The final Stop gets `stop`.

## Where it would branch

- **Denied merge** (step 16, with an unreviewed revision `b2`): ← `{decision: deny, denial: {code: revision_not_reviewed, message: "merge is allowed only for the reviewed revision b1, not b2.", recovery: "Request approve_release again for b2, or merge b1.", details: {reviewed_revision: "b1"}}}`. The kit returns it as `permissionDecisionReason`, which the agent sees.
- **Failed checkpoint** (step 12, `code_quality` median below `min_score`): `status.last_decision` is `attempt_failed`, with each member's band and rationale. `permitted_transitions` shows `approve_release` with `attempts: 1`, and `rework` toward implementation. The agent reworks and requests again with `expected_attempts: 1`.
- **Lost response** (step 12 retried with the same arguments): the gateway derives the same idempotency key, and the engine returns the stored `attempt_open` instead of opening a second attempt.
- **Exhausted stage task** (step 9, a limit reached with criteria still unmet): ← `{verdict: stop, reason: "Stage task exhausted; the owner decides: extend, rework, or abandon."}`, and `status.pending.escalations` lists it (sequence 6).
