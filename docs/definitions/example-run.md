# Example run, scored by hand

From spike 003 (`specs/003-definition-model-draft/`), updated by spike 010 (Q26: git is the evidence channel; Q27: Jev is the only provider). One run of `sdlc-reference.yaml`, with a failed checkpoint attempt, a rework loop, and an override, scored with the rules in `schema.md`. It follows the shape of test-bed tasks T06 (failed checkpoint, rework) and T07 (override) in `docs/test-bed.md`.

## Accounts

- **owner** starts the run and drives it with Claude Code. It holds the lease throughout.
- **approver** is the second UI account. It never holds a lease on this run, so separation of duties lets it approve and override. In the MVP one person holds both accounts (spike 003, Q20).

## What happens

| # | Event | Revision | Effect |
| --- | --- | --- | --- |
| 1 | owner starts the run | – | Stage `requirements`, visit 1 |
| 2 | Driver commits the requirements file, pushes r1, requests `start_implementation` | r1 | Stage check `requirements_present` passes in the runner. Stage `implementation`, visit 1 |
| 3 | Driver commits the change, pushes r2, requests `verify` | r2 | No checkpoint: accepted. Stage `verification`, visit 1 |
| 4 | Driver runs the tests locally, requests `approve_release` | r2 | Attempt 1 starts |
| 5 | `diff_present` passes in the runner. `tests_green` runs `./mvnw -B verify` in the runner at r2 and exits 1 (two tests fail). The rest are not evaluated | r2 | Attempt 1 fails. Run stays in `verification` |
| 6 | Driver requests `rework` | r2 | Rework edge: accepted. Stage `implementation`, visit 2 |
| 7 | Driver fixes the code, pushes r3, requests `verify` | r3 | Stage `verification`, visit 2 |
| 8 | Driver runs the tests locally, pushes r3, requests `approve_release` | r3 | Attempt 2 starts |
| 9 | `diff_present` passes and `tests_green` exits 0, both in the runner at r3 | r3 | |
| 10 | `code_quality`: three runner evaluators, each in its own sandbox at r3, return 4, 5, 7. Median 5 < 6: fails. In parallel, a runner input packet job returns the diff and requirements at r3. In the shadow decision tree, Jev answers `weak` to `code_quality_band`, so `has_defect` is asked; Jev answers `true` at 0.91, which concludes `likely_defect` | r3 | Attempt 2 stops on the failed required step |
| 11 | approver overrides `code_quality` in the UI: "Structure issues tracked as a follow-up; release is urgent." | r3 | Step `overridden`; attempt 2 resumes at the next step |
| 12 | `docs_updated` returns 4 from one runner evaluator | r3 | |
| 13 | `release_approval` waits in the UI inbox; approver opens the compare view at r3 and approves | r3 | Attempt 2 passes. Stage `release` |
| 14 | Merge hook calls `authorize_action merge` | r3 | Allowed: run active, stage `release`, revision equals attempt 2's r3 |
| 15 | The merge creates merge commit r4 on the base branch. Driver requests `finish` with the reviewed revision r3 | r3 | Stage check `merge_recorded` passes in the runner: the base branch contains r3. Stage `complete`; run `completed` |

Variations:

- Had owner pushed another commit (r3a) after step 13, the merge in step 14 would have been denied: the revision must equal the one the checkpoint reviewed.
- Had the PostgreSQL service never become ready in step 5, `tests_green` would be *not evaluated*, not failed. Attempt 1 would not pass, but it would cost no deduction, and the driver could request again.
- Attempt 2 used four runner evaluations (three for the council, one for `docs_updated`), all on the owner's subscription, plus one input packet job for Jev's tree, which uses no model, and two Jev calls.

## Score

The path taken is `requirements → implementation → verification → implementation → verification → release → complete`. Only `start_implementation`, `approve_release`, and `finish` have checkpoints. Each step counts its latest evaluation, which is attempt 2 for `approve_release`.

| Step | Runs in | Result | Raw | Formula | Earned | Possible |
| --- | --- | --- | --- | --- | --- | --- |
| `requirements_present` | runner | passed | exit 0 | pass/fail | 0 | 0 |
| `diff_present` | runner | passed | – | pass/fail | 0 | 0 |
| `tests_green` | runner | passed | exit 0 | pass/fail | 10 | 10 |
| `code_quality` | runner, ×3 | overridden | 5 (median of 4, 5, 7) | 20 × (5 − 1) / (10 − 1) | 8.889 | 20 |
| `docs_updated` | runner | passed (optional) | 4 | 5 × (4 − 1) / (5 − 1) | 3.750 | 5 |
| `release_approval` | UI | passed | approved | pass/fail | 0 | 0 |
| `merge_recorded` | runner | passed | – | pass/fail | 0 | 0 |
| **Total** | | | | | **22.639** | **35** |

Jev's `weak` for `code_quality` is recorded next to the council's median band (`weak`) and the approver's override, together with the tree's follow-up answer and its `likely_defect` conclusion. None of it earns points or changes the run.

| Line | Points |
| --- | --- |
| Normalized: 22.639 ÷ 35 × 100 | 64.683 |
| Failed attempt 1 (`tests_green`) | −5 |
| Rework loop (`rework`) | −5 |
| Failed attempt 2 (`code_quality`, overridden) | −5 |
| **Run score** | **49.683, shown as 49.7** |

Classification: `overridden`, because the run completed with an override. It is compared with other runs of the same definition version only, and the quality view shows the flag. The task's hidden acceptance tests, if any, are measured afterwards as an outcome and compared with this score; they are not part of it.
