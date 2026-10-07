---
name: sdd-implement
description: Implement a planned feature spec task by task, ticking each task only when its check passes. Use when a spec in specs/ is planned or in progress.
argument-hint: <spec ID> [task ID, e.g. T3]
---

# Implement

Arguments: $ARGUMENTS (spec ID, then an optional task ID)

## 1. Gate

- Open the spec folder `specs/<ID>-*/`. If no ID was given, run `specs/_kit/sdd next` and ask which one.
- If the spec is a spike (`type: spike`), stop and follow the `sdd-spike` skill instead.
- The status must be `planned` or `in-progress`. If it is `draft` or `approved`, stop and suggest `/sdd-plan <ID>`.
- Running this command is the user's approval of the plan. If the status is `planned`, set it to `in-progress` and add a changelog line.
- Read `specs/constitution.md`, `spec.md`, `plan.md`, and `tasks.md`.

## 2. Work the tasks

Other sessions may be working on the same spec. Never pick a task by reading `tasks.md`; claim it, and work only on a task you hold.

1. Claim the task: `specs/_kit/sdd claim <ID> <Tn>` for a given task, or else `specs/_kit/sdd claim <ID> next`, which prints the task it claimed (or the one this session already holds). If the claim fails, stop and report its message: who holds which task, or what the free tasks wait on.
2. Do exactly that task, following the plan. Test first where the task calls for it.
3. Run the task's check. Then run the build and the relevant tests so the tree stays green.
4. Only when the check passes, tick the task `- [x]` (change only that line of `tasks.md`), then run `specs/_kit/sdd release <ID> <Tn> --done`.
5. If the user has asked for commits, commit with a message starting `[<ID>] T<n>`; otherwise suggest that message.
6. Continue from step 1 with `claim <ID> next`, unless a task ID was given.

Stop and ask the user when: a check fails and the fix is not obvious, the plan turns out wrong, a task needs a decision the plan does not cover, or something requires a human action. If you stop with the task unfinished, keep the claim only if this session will resume it; otherwise release it with `specs/_kit/sdd release <ID> <Tn>`.

When reality disagrees with the documents, update them before the code (Article 12):
- Plan wrong: add a row to the plan's Deviations, and change the design or tasks.
- Spec wrong (behavior, scope, or ACs change): stop, explain, and get the user's agreement before editing the spec and its changelog.
- New work found: add a new task; never do untracked extra work.

## 3. Finish

- Make sure this session holds no claim it will not resume (`specs/_kit/sdd claims`). Run `specs/_kit/sdd status --write`.
- Report briefly: tasks done in this session, what is left, deviations recorded, and the next step: `/sdd-implement <ID>` again, or `/sdd-verify <ID>` once every task is ticked.
