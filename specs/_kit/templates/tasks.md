# {{ID}} · Tasks: {{TITLE}}

<!--
Rules:
- Ordered. A task starts once every task before it is ticked, except that consecutive tasks marked [P]
  (written right after the ID: **T3** [P]) can run at the same time, in different sessions.
- Small: half a day at most, and each one leaves the build and tests green.
- Each names the files or modules it touches, how it is checked, and the acceptance criteria it serves (→ AC-n).
- Write the check as a command in backticks after "check:", which `specs/_kit/sdd done <ID> <Tn>` runs from the
  repository root, or as "check: manual: <what a person checks>" when no command can prove it.
- Prefer test first: a task that adds behavior starts by adding the failing test for it.
- Tick a task only with `sdd done`, which ticks it only when its check passes. New work found along the way becomes a new task, not a silent extra.
-->

## Tasks

- [ ] **T1** <imperative description> · `path/or/module` · check: `<command>` → AC-1

## Verification

- [ ] **V1** Every acceptance criterion verified, with evidence below
- [ ] **V2** PLAN.md and BACKLOG.md updated for this spec

## Evidence

| Acceptance criterion | Result | Evidence (test, command output, manual check) |
| --- | --- | --- |
| AC-1 | | |
