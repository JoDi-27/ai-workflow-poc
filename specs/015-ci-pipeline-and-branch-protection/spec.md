---
id: 015
title: "CI pipeline and branch protection"
type: feature
status: done
phase: 0
depends_on: []
refs: ["PLAN Phase 0 / Scaffolding / CI pipeline and branch protection", "BACKLOG Hooks to engine (GitHub branch protection stays the real gate)"]
updated: 2026-11-01
---

# 015 · CI pipeline and branch protection

## Problem

Claude Code sessions write most of this repository's code. Today, every check that keeps that work honest runs on the developer's laptop: the commit message hook, `sdd done`, and the build and tests that sessions are told to run. A session can skip them, weaken a check, or push straight to `main`, as happened on 2026-11-01 when kit changes went to `main` with no review. Nothing off the laptop confirms that what lands on `main` builds, passes its tests, and follows the kit.

The constitution already holds this principle for the product: record on the laptop, enforce gates off it (article 8). This spec applies it to the repository itself. Every change reaches `main` through a pull request, and a set of automated checks that no session can skip must pass first.

It has to land before product code does, so that the engine skeleton's first commit already goes through it. Only repository checks exist today. The engine and UI skeleton specs add their build, test, coverage, and mutation checks to this pipeline when they create that code.

It also moves PLAN Phase 0 toward its exit criteria. The BACKLOG decision on the merge gate (spike 005) relies on GitHub branch protection as the real gate for the product's test bed, and this spec sets up the same mechanism here first.

## Scope

**In:**

- Automated checks that run on every pull request to `main` and on every push to `main`.
- The first set of checks, for what exists today: the `.claude/README.md` inventory is current, `specs/STATUS.md` is current, every spec parses without warnings, and every shell script in the kit and hooks is valid.
- Merge rules on `main`: changes arrive only through a pull request, the required checks must pass, and history cannot be rewritten or the branch deleted.
- A documented way for later specs to add a check and make it required.
- Updating how sessions and people work: `CONTRIBUTING.md`, `CLAUDE.md`, `specs/README.md`, and the kit skills say that work reaches `main` through a pull request.
- The `sdd-reviewer` checklist flags changes to the checks or the merge rules, so they cannot weaken quietly.

**Out:**

- Engine build, unit and integration tests, module rules, coverage, and mutation testing: the engine skeleton spec, which adds them as required checks.
- UI build and tests: the UI skeleton spec.
- End-to-end deployment to a local cluster in CI: the Kubernetes manifests spec, if it is wanted.
- CI or pull request status as system-attested evidence for the engine: BACKLOG "Next" (System-attested evidence).
- Any check that calls a model: article 4 allows unattended model use only in runner evaluations.
- Releases and deployments.
- Checking commit messages in CI: the local `commit-msg` git hook (`.githooks/commit-msg`, set up as `CONTRIBUTING.md` says) and the Claude Code hook already check them (Q4).

## User scenarios

### S1 · A session lands a change

A Claude Code session has finished a task on its branch and wants it on `main`.

1. It pushes its branch and opens a pull request to `main`.
2. The checks run and report on the pull request.
3. When every required check passes, the owner merges it. If one fails, the pull request cannot be merged until a new commit fixes it.

### S2 · A direct push is refused

A session, or the owner, tries to push commits straight to `main`.

1. The hosting service refuses the push and says that `main` takes changes only through pull requests.

### S3 · A later spec adds a check

The engine skeleton spec adds the engine's build and tests.

1. It adds the new check, following the documented way.
2. It makes the check required, so a pull request that fails it cannot be merged.

### S4 · A session tries to weaken a check

A session stuck on a failing check changes the check instead of fixing the code.

1. The change is in the pull request's diff, and the `sdd-reviewer` report flags it.
2. The owner sees it before merging.

## Requirements

Checks:

- **R1** When a pull request to `main` is opened or updated, the CI shall run every check and report each result on the pull request.
- **R2** When commits are pushed to `main`, the CI shall run every check and report the result on the commit.
- **R3** The CI shall fail the inventory check if `.claude/README.md` differs from what `.claude/bom.py` generates.
- **R4** The CI shall fail the status check if `specs/STATUS.md` differs from what `specs/_kit/sdd status --write` generates, or if the spec board reports a warning.
- **R5** The CI shall fail the scripts check if any shell script in `specs/_kit/`, `.claude/hooks/`, `.githooks/`, or `.github/scripts/` is not valid shell syntax.
- **R6** The CI shall run only deterministic checks and shall not call a model.

Merge rules:

- **R7** If a push to `main` does not come from merging a pull request, then the hosting service shall refuse it.
- **R8** While any required check has not passed on a pull request's latest commit, the hosting service shall not allow the pull request to be merged.
- **R9** The hosting service shall refuse a force push to `main` and the deletion of `main`.
- **R10** Nobody shall be able to bypass R7–R9, including the owner as repository admin (Q1).
- **R11** A pull request shall need no approving review to be merged; passing the required checks and the owner's decision to merge are enough (Q2).
- **R12** The hosting service shall allow only merge commits into `main`, so every task commit stays on `main` and each merged pull request is marked by a merge commit (Q3, article 12).

Ways of working:

- **R13** The repository's working rules (`CONTRIBUTING.md`, `CLAUDE.md`, `specs/README.md`, and the kit skills) shall say that changes reach `main` only through a pull request whose required checks pass.
- **R14** The repository shall document how a later spec adds a check and makes it required.
- **R15** When a pull request changes a check's definition or the merge rules, the `sdd-reviewer` agent shall report it as a finding.

## Acceptance criteria

- **AC-1** Given a pull request to `main`, when it is opened and again when a commit is added to it, then every check runs and its result shows on the pull request. (R1)
- **AC-2** Given a merge to `main`, when it lands, then the checks run on the new `main` commit and the result shows on it. (R2)
- **AC-3** Given a pull request whose branch has a hand edit to the generated part of `.claude/README.md`, when the checks run, then the inventory check fails; after reverting the edit, it passes. (R3)
- **AC-4** Given a pull request where `specs/STATUS.md` is out of date with a spec's status, when the checks run, then the status check fails; after regenerating it, it passes. The same holds for a spec with an unknown status. (R4)
- **AC-5** Given a pull request that adds a syntax error to a hook script, when the checks run, then the scripts check fails; after fixing it, it passes. (R5)
- **AC-6** Given the check definitions, when they are read, then no step calls a model or holds model credentials. (R6)
- **AC-7** Given a local commit on `main`, when the owner pushes it to `main` directly with admin rights, then the push is refused. (R7, R10)
- **AC-8** Given a pull request with a failing required check, when the owner tries to merge it, including with admin rights, then the merge is refused; after a commit makes the check pass, the merge is allowed with no approving review. (R8, R10, R11)
- **AC-9** Given `main`, when the owner tries a force push and a deletion of `main`, then both are refused. (R9, R10)
- **AC-10** Given a pull request with passing checks, when the owner merges it, then only the merge commit method is offered, and afterwards `main` contains the pull request's task commits and a merge commit. (R12)
- **AC-11** Given `CONTRIBUTING.md`, `CLAUDE.md`, `specs/README.md`, and the kit skills, when they are read, then they say changes reach `main` only through a pull request whose required checks pass, and none of them says to commit or push to `main` directly. (R13)
- **AC-12** Given the documentation of how to add a check, when a reviewer follows it to add a trivial check on a test branch, then the new check runs on that branch's pull request and is required for merging; the test check is removed afterwards. (R14)
- **AC-13** Given a pull request that lowers a check's threshold or removes a check, when `sdd-reviewer` runs in verify mode, then its report has a finding for that change. (R15)

## Open questions

- Q1: May anyone, including the owner as admin, bypass the merge rules? A: Nobody. Sessions use the owner's credentials, so any bypass the owner holds, a session holds too. (owner, 2026-11-01)
- Q2: How many approving reviews does a pull request need? A: None. GitHub does not let the owner approve their own pull request; the required checks are the hard gate and merging is the owner's review. Revisit when a second person joins. (owner, 2026-11-01)
- Q3: Which merge methods are allowed on `main`? A: Merge commits only, to keep every `[NNN] Tn` commit on `main` (article 12). (owner, 2026-11-01)
- Q4: Should CI also check commit message prefixes? A: No. The `commit-msg` git hook added on 2026-11-01 (`.githooks/commit-msg`, `CONTRIBUTING.md`) checks every commit in a clone that ran the setup, and the Claude Code hook checks Claude's commits. (owner, 2026-11-01)

## Changelog

- 2026-11-01 Created.
- 2026-11-01 Q1–Q4 answered: no bypass, no required approvals, merge commits only, no CI commit message check. Added `.githooks/` to the scripts check and `CONTRIBUTING.md` to the working rules.
- 2026-11-01 Approved (`/sdd-plan`).
- 2026-11-01 R5 also covers `.github/scripts/`, where planning put the CI scripts (plan review).
- 2026-11-01 Planned: `plan.md` and `tasks.md` (15 tasks).
- 2026-11-01 Implementation started (`/sdd-implement`).
- 2026-11-01 Verified and closed (`/sdd-verify`): all 13 ACs pass. T16 and T17 added from the verify review. Follow-up: BACKLOG "Guard the live merge rules".
