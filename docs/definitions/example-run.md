# Example run, scored by hand

From spike 003 (`specs/003-definition-model-draft/`). One run of `sdlc-reference.yaml`, with a failed checkpoint attempt, a rework loop, and an override, scored with the rules in `schema.md`. It follows the shape of test-bed tasks T06 (failed checkpoint, rework) and T07 (override) in `docs/test-bed.md`.

## Accounts

- **owner** starts the run and drives it with Claude Code. It holds the lease throughout.
- **approver** is the second UI account. It never holds a lease on this run, so separation of duties lets it approve and override. In the MVP one person holds both accounts (spike 003, Q20).

## What happens

| # | Event | Revision | Effect |
| --- | --- | --- | --- |
| 1 | owner starts the run | – | Stage `requirements`, visit 1 |
| 2 | Driver submits `requirements_doc`, requests `start_implementation` | r1 | No checkpoint: accepted. Stage `implementation`, visit 1 |
| 3 | Driver submits `diff`, requests `verify` | r2 | No checkpoint: accepted. Stage `verification`, visit 1 |
| 4 | Driver runs the tests locally, submits `local_test_summary`, pushes r2, requests `approve_release` | r2 | Attempt 1 starts |
| 5 | `diff_present` passes in the engine. `tests_green` runs `./mvnw -B verify` in the runner at r2 and exits 1 (two tests fail). The rest are not evaluated | r2 | Attempt 1 fails. Run stays in `verification` |
| 6 | Driver requests `rework` | r2 | Rework edge: accepted. Stage `implementation`, visit 2 |
| 7 | Driver fixes the code, submits a new `diff`, requests `verify` | r3 | Stage `verification`, visit 2 |
| 8 | Driver runs the tests locally, pushes r3, requests `approve_release` | r3 | Attempt 2 starts |
| 9 | `diff_present` passes; `tests_green` exits 0 in the runner at r3 | r3 | |
| 10 | `code_quality`: three runner evaluators, each in its own sandbox at r3, return 4, 5, 7. Median 5 < 6: fails. Laya, shadowing, answers `weak` | r3 | Attempt 2 stops on the failed required step |
| 11 | approver overrides `code_quality` in the UI: "Structure issues tracked as a follow-up; release is urgent." | r3 | Step `overridden`; attempt 2 resumes at the next step |
| 12 | `docs_updated` returns 4 from one runner evaluator | r3 | |
| 13 | `release_approval` waits in the UI inbox; approver approves | r3 | Attempt 2 passes. Stage `release` |
| 14 | Merge hook calls `authorize_action merge` | r3 | Allowed: run active, stage `release`, revision equals attempt 2's r3 |
| 15 | Driver submits `merge_receipt`, requests `finish` | r4 | Stage check `merge_recorded` passes in the engine. Stage `complete`; run `completed` |

Variations:

- Had owner pushed another commit (r3a) after step 13, the merge in step 14 would have been denied: the revision must equal the one the checkpoint reviewed.
- Had the PostgreSQL service never become ready in step 5, `tests_green` would be *not evaluated*, not failed. Attempt 1 would not pass, but it would cost no deduction, and the driver could request again.
- Attempt 2 used four runner evaluations (three for the council, one for `docs_updated`), all on the owner's subscription.

## Score

The path taken is `requirements → implementation → verification → implementation → verification → release → complete`. Only `approve_release` and `finish` have checkpoints. Each step counts its latest evaluation, which is attempt 2 for `approve_release`.

| Step | Runs in | Result | Raw | Formula | Earned | Possible |
| --- | --- | --- | --- | --- | --- | --- |
| `diff_present` | engine | passed | – | pass/fail | 0 | 0 |
| `tests_green` | runner | passed | exit 0 | pass/fail | 10 | 10 |
| `code_quality` | runner, ×3 | overridden | 5 (median of 4, 5, 7) | 20 × (5 − 1) / (10 − 1) | 8.889 | 20 |
| `docs_updated` | runner | passed (optional) | 4 | 5 × (4 − 1) / (5 − 1) | 3.750 | 5 |
| `release_approval` | UI | passed | approved | pass/fail | 0 | 0 |
| `merge_recorded` | engine | passed | – | pass/fail | 0 | 0 |
| **Total** | | | | | **22.639** | **35** |

Laya's `weak` for `code_quality` is recorded next to the council's median band (`weak`) and the approver's override. It earns nothing.

| Line | Points |
| --- | --- |
| Normalized: 22.639 ÷ 35 × 100 | 64.683 |
| Failed attempt 1 (`tests_green`) | −5 |
| Rework loop (`rework`) | −5 |
| Failed attempt 2 (`code_quality`, overridden) | −5 |
| **Run score** | **49.683, shown as 49.7** |

Classification: `overridden`, because the run completed with an override. It is compared with other runs of the same definition version only, and the quality view shows the flag. The task's hidden acceptance tests, if any, are measured afterwards as an outcome and compared with this score; they are not part of it.
