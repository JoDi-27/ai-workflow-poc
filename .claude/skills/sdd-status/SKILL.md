---
name: sdd-status
description: Show the progress board of specs in specs/ and what to work on next, or the detailed state of one spec. Use when asked about progress, status, or what to do next.
argument-hint: "[spec ID]"
---

# Status

Arguments: $ARGUMENTS

## Without an ID

- Run `specs/_kit/sdd status --write` and show the board.
- Summarize in a few lines: what is in progress, what is next and the command for it (from "Next up"), what other sessions hold (from "Claims", pointing out stale ones and that `specs/_kit/sdd release <ID> [Tn] --force` frees them), what is blocked or waiting, and specs with open questions.
- Relate it to the roadmap: which PLAN.md phase is current, and which of its items have no spec yet. Suggest the command for the next one in PLAN.md order: `/sdd-spike` for a spike, `/sdd-specify` otherwise.

## With an ID

Read the spec folder and report: status, open questions, tasks done and left (with the next free task and the tasks other sessions hold, from `specs/_kit/sdd claims`), plan deviations, failing or missing evidence, and the next command to run.

Do not change any spec files, except regenerating `specs/STATUS.md`.
