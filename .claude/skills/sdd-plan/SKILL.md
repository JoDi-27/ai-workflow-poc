---
name: sdd-plan
description: Approve a spec and write its technical plan (plan.md) and task list (tasks.md). Use when a feature spec in specs/ is ready to be planned; spikes are run with /sdd-spike instead.
argument-hint: <spec ID>
---

# Plan

Spec: $ARGUMENTS

You write the HOW for one spec. You do not implement.

## 1. Gate

- Open `specs/$ARGUMENTS-*/spec.md`. If no ID was given, run `specs/_kit/sdd next` and ask which spec to plan.
- If the spec is a spike (`type: spike`), stop: spikes have no plan. Suggest `/sdd-spike <ID>`.
- If the spec has any `[NEEDS CLARIFICATION` markers, stop: list them and suggest `/sdd-specify <ID>`.
- If any spec in `depends_on` is not `done`, say so and ask whether to plan anyway.
- Claim the spec: `specs/_kit/sdd claim <ID>`. If another session holds it, stop and report who.
- Running this command is the user's approval of the spec. If the status is `draft`, set it to `approved` and add a changelog line.

## 2. Load context

- Read `specs/constitution.md` and the spec.
- Read the code, configuration, and specs that the work touches. Read `plan.md` of the specs it depends on.
- Check current documentation for every library, SDK, or tool the plan relies on, and note the versions (Article 15). When there are three or more, launch one `spike-researcher` agent per library in a single message, each with a focus such as "current stable version of <library>, and the documented way to <what the plan needs>; cite the docs page and date". Use its findings, not memory, for the versions.
- For engine work, also load the `engine-change` skill.

## 3. Write the plan and tasks

- Create `plan.md` and `tasks.md` in the spec folder from `specs/_kit/templates/`, replacing `{{ID}}` and `{{TITLE}}`.
- **plan.md:** the approach, the constitution check (every article the work touches, how it complies, and a justification for any exception), the design, requirement coverage (every R maps to part of the design), decisions with alternatives, the test strategy (every AC maps to how it is verified), and risks.
- **tasks.md:** ordered tasks, each small (half a day at most), leaving the build green, naming the files or modules it touches, its check, and the ACs it serves (`→ AC-n`). Write the check as a command in backticks (``check: `./mvnw -q -pl engine-core test` ``), which `sdd done` runs, or as `check: manual: <what a person checks>` when no command can prove it. Test first where it fits. Mark tasks that can run in parallel with `[P]` right after the task ID (`- [ ] **T3** [P] …`): consecutive `[P]` tasks can be claimed by different sessions at once, while any other task waits for every task before it. Use `[P]` only for tasks that touch different files and do not need each other's code. Keep the Verification and Evidence sections.
- If planning shows the spec is wrong or incomplete, fix the spec first and add a changelog line. Do not add requirements in the plan.

## 4. Review

- Launch the `sdd-reviewer` agent in `plan` mode with the spec folder.
- Fix the blocking findings and the ones you agree with in the plan or tasks. Keep the others for the report, with a line on why you left each.

## 5. Finish

- Set `status: planned` and `updated` in the spec. Release the spec (`specs/_kit/sdd release <ID>`) and run `specs/_kit/sdd status --write`.
- Report briefly: the key decisions, the number of tasks, any risks, the reviewer findings you did not apply, and the next step: the user reviews the plan, then runs `/sdd-implement <ID>`.
