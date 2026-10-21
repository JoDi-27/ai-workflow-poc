# Test bed

From spike 002 (`specs/002-test-bed-target-repository-and-task-set/`), where every point is decided in the Log. This is where runs do real work and which tasks they do. The other spikes, the seed data, and the Phase 4 validation all use these cases.

## Repository

- **Repository:** `sample-crm`, at `github.com/JoDi-27/sample-crm`, cloned locally at `/Users/jodi/repos/personal/sample-crm`. It is public and owned by the user, so merging to it is harmless.
- **Stack:** a Java Spring Boot REST service for contacts, companies, and deals, on PostgreSQL, on Java 25 with the Maven wrapper. On the laptop, tests start PostgreSQL with Testcontainers, so the laptop needs a Docker-compatible runtime. When a PostgreSQL is given by environment variables, the tests use it instead.
- **CI:** a minimal GitHub Actions workflow that runs `./mvnw -B verify` against a `services: postgres` container, given to the tests by environment variables. In the MVP, it is only used to spot-check driver-reported results. Its check runs carry the commit SHA, so it can later provide system-attested evidence.
- **Baseline:** the tag `baseline` in `sample-crm`, at commit `0916dbfcb9be0299f0e83acc1043d153331688cb` on `main` (2026-10-19, spec 004). It is T01's base commit. `sample-crm` has its own copy of the spec kit, and its spec 001 (`specs/001-crm-service-baseline/`) describes the service as a normal product. The planted defects and existing tests that T02, T04, T06, and T07 rely on are specified only here, in spec 004 (`specs/004-test-bed-baseline/`), so a driver reading `sample-crm` finds nothing that gives them away. `docs/test-bed/baseline-checks.sh` checks the planted page offset and email storage against a running baseline.
- **Process inside `sample-crm`:** its spec kit covers baseline and maintenance work only. Work on `task/*` and `base/*` branches follows the run's instructions; the reference workflow does not use the kit inside runs in v1; using it is a candidate v2 change for the feedback loop (spike 003, Q29).

## Runner checks

From spike 002, G4. The runner runs `sample-crm`'s tests in a throwaway Kubernetes pod, on a local cluster now and GKE later.

- **Command:** `./mvnw -B verify`, the same as CI, with the database given by environment variables.
- **Image:** a generic `eclipse-temurin:25-jdk` image. Its Dockerfile lives in this repository, next to the reference definition, pinned by digest.
- **Database:** PostgreSQL as a native sidecar in the pod, gated by a startup probe. A database that never starts means the job did not run, so the step is *not evaluated* rather than *failed*.
- **Isolation:** the pod is never privileged, and its only egress is an in-cluster Maven proxy.
- **Budget:** a check may take up to 15 minutes.
- Hidden acceptance tests never enter the gate sandbox: they are an outcome measured outside the run score and compared with it (spike 003, Q21).

## Branches and reruns

- Tasks are run in order, T01 to T12, and their merges accumulate on `main`.
- Each task records its **base commit**: the commit on `main` that its run started from. T01's is the `baseline` tag, `0916dbf`.
- A run's branch is `task/Tnn/v1`. Its subject key is the repository and this branch.
- A rerun on a new definition version branches `task/Tnn/vN` from the task's base commit, so it starts from identical code. It merges into a throwaway branch `base/Tnn/vN`, created at the same base commit. `main` is untouched, and the protected action is still exercised.

## People

- The user drives runs and owns them.
- Human steps are approved from a second UI account the user controls, so separation of duties can be exercised. This is an accepted MVP risk, since one person holds both roles.
- Concurrent users are exercised by running T03 and T12 at the same time, one from each account.

## Tasks

"Planted" is what the baseline or the task setup contains on purpose so that the expected path happens.

| # | Category | Task | Planted | Expected path and result | Exercises |
| --- | --- | --- | --- | --- | --- |
| T01 | Easy, clean | Add `GET /contacts/count`, optionally filtered by company | — | First attempt passes; high score | Happy path |
| T02 | Easy bug fix | "Page 2 of `/contacts` repeats the last contact of page 1" | Off-by-one in pagination; no failing test | Fixed with a regression test; high score | Driver writes the test |
| T03 | Medium feature | Deal stages `LEAD → QUALIFIED → WON / LOST`; invalid transitions return 409 | — | Completes; likely one failed deterministic attempt | Failed attempt; concurrent users |
| T04 | Refactor | Move duplicated request validation from controllers into one validator, with no behavior change | Duplicated validation | Tests unchanged and green; the code-quality step decides the score | Reasoning step |
| T05 | Ambiguous | "Make contact search better" | Vague requirement | Human step rejects at `release_approval`; rework, then completes | Rework loop; human reject |
| T06 | Quality trap | Reject duplicate contacts by email on create | Visible tests use exact-match emails only | Visible tests pass with a naive check; the reasoning step lands low (case and whitespace not normalized); checkpoint fails; rework | Failed checkpoint; rework |
| T07 | Contradiction | "Deleting a company deletes its contacts" | An existing test asserts contacts survive with the company unset | Deterministic step fails on the old test; the approver account overrides with a reason (separation of duties binds overrides) | Override |
| T08 | Too big | CSV import and export for contacts, companies, and deals, with a validation report | A low iteration limit | Limit exhausted; escalates to the owner; owner extends and it completes | Exhaustion escalation |
| T09 | Impossible | "Sync contacts with the Acme CRM API" | No such API, no credentials | Driver cannot proceed; owner ends the run; score excluded | Excluded run |
| T10 | Protected action | Add OpenAPI descriptions to the deal endpoints | A scripted early-merge attempt | Merge denied before the checkpoint passes, allowed after | Protected action |
| T11 | Risk | Add `GET /contacts/search?filter=` taking a free-form filter | Building SQL from the input is the easy route | Tests pass; the human step catches the injection risk and rejects; rework to parameterized queries | Human catches a risk |
| T12 | Long | Activity timeline: notes and calls on contacts and deals, paginated | — | Likely pauses on a usage limit or interrupt, then resumes and completes | Pause and resume; concurrent users |

Separation of duties is exercised on every human step. The v1 reference workflow has no planted weakness: the weakest step should emerge from real runs. If none stands out after the first seed runs, revisit this before the v2 cycle.

### Rerun set

On a new definition version, rerun T01, T03, T04, T05, T06, and T11, each from its base commit.

### Task records

Each task gets a record holding: base commit, task text, acceptance criteria, hidden acceptance tests, planted defect, expected path (attempts, rework, escalations), expected outcome and score class, and the step expected to fail.

Claude drafts the expected results and hidden acceptance tests in a session separate from any run, and the user reviews them. They live in this repository, under `docs/`, never in `sample-crm`, so the driver never sees them. They are not written yet; they need the baseline.

## Laya's first cases

Laya is out of the MVP (spike 010, Q27): it needs training to be useful. These cases stay as the first labeled set for that later training, and spike 007 used them for Jev too.

- Five tasks give Laya's first labeled local cases, each as a hand-made good and flawed variant, so ten cases:

  | Task | Good variant | Flawed variant |
  | --- | --- | --- |
  | T01 | Count with filter and tests | No test for the filter |
  | T03 | All invalid transitions rejected | One invalid transition allowed |
  | T04 | Pure refactor | A silent behavior change |
  | T06 | Email normalized for case and whitespace | Exact-match only |
  | T11 | Parameterized query | SQL built from the input (injection) |

- The user labels them blind, before seeing the Claude evaluator's band.
- Outputs of real runs of the same tasks are added later as the held-out set.
- The typed question and the label set are settled by spike 6; until then, the labels are provisional rubric bands.
- `laya-typed-decisions` has a 1024-token context, so each case's packet (rendered rubric, diff or summary, labels) must fit in it.
