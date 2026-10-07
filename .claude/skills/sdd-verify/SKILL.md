---
name: sdd-verify
description: Verify a spec's acceptance criteria (or a spike's answers) with evidence, then close it and update PLAN.md and BACKLOG.md. Use when all of a spec's tasks are ticked.
argument-hint: <spec ID>
---

# Verify

Spec: $ARGUMENTS

You prove that the spec is met. You do not add features.

## 1. Gate

- Open `specs/$ARGUMENTS-*/`. The status must be `in-progress`.
- Claim the spec: `specs/_kit/sdd claim <ID>`. If another session holds the spec or one of its tasks, stop and report who.
- Every task must be ticked; for a spike, every goal question and question for the user, and the first deliverable (recommendation agreed). If not, list what is left and suggest `/sdd-implement <ID>` (features) or `/sdd-spike <ID>` (spikes).

## 2a. Feature: check every acceptance criterion

- For each AC in `spec.md`, run its verification from the plan's test strategy: the named tests, commands, or a manual check.
- For a manual check that needs the user (for example, a UI flow), ask them to do it and record what they report.
- Fill one row of the Evidence table in `tasks.md` per AC: pass or fail, and concrete evidence (test name, command and its relevant output, or who checked what).
- Also confirm: the full build and test suite pass, every requirement is covered by a passing AC, and the plan's Deviations reflect what was built.

If any AC fails, do not close the spec. Add a fix task for each failure to `tasks.md`, keep `in-progress`, release the spec, report the failures, and suggest `/sdd-implement <ID>`.

## 2b. Spike: check the answers

- Every goal question has findings labelled as decisions, facts with sources, or assumptions. Remaining assumptions are either confirmed by the user or listed as Follow-ups.
- The Inbox is empty, the Log covers every answer, the Recommendation table is filled in and agreed, and Follow-ups are listed.
- Each BACKLOG.md item the spike settles is updated in place: ticked, with the choice and a link to the spike.
- Each Follow-up exists as a new spec, a BACKLOG.md item, or an agreed decision to drop it. Ask the user which before creating new specs.

## 3. Close

- Tick the Verification tasks (features) or Deliverables (spikes).
- Tick the PLAN.md checkbox(es) for the items in the spec's `refs` that are now complete. Update BACKLOG.md items it settles, if not done already.
- Set `status: done` and `updated` in the spec, and add a changelog line.
- Release the spec and clear its leftover claims: `specs/_kit/sdd release <ID>`, then `specs/_kit/sdd claims --prune`. Run `specs/_kit/sdd status --write`.
- Report briefly: the result per AC (or per question), the PLAN and BACKLOG items updated, and what `specs/_kit/sdd next` now shows.
