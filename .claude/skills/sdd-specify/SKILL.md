---
name: sdd-specify
description: Create a new feature spec in specs/, or refine and clarify an existing one. Use when starting a new piece of work or picking up a PLAN.md item; spikes go to /sdd-spike.
argument-hint: <idea | PLAN.md item | spec ID>
---

# Specify

Input: $ARGUMENTS

You write the WHAT and WHY of one slice of work. You do not plan or implement.

## 1. Load context

- Read `specs/README.md` (the workflow) and `specs/constitution.md`.
- Read the parts of `PLAN.md`, `BACKLOG.md`, and `WORKFLOW_ENGINE_INTENT.md` that relate to the input. Use grep on headings rather than reading the intent doc whole.
- Run `specs/_kit/sdd status` to see existing specs, so you can set `depends_on` and avoid duplicates.

## 2. Create or open the spec

- If the input is a spec ID (for example `003`), claim it (`specs/_kit/sdd claim 003`), open `specs/003-*/spec.md`, and go to step 4. If another session holds it, stop and report who.
- Otherwise pick the type: `spike` if the work answers questions or settles decisions (all PLAN.md Phase 0 spikes), `feature` if it delivers working behavior.
- Spikes are run as an interview by `/sdd-spike`, not by this skill. For a spike (new, or an existing spike ID), stop here and follow the `sdd-spike` skill instead.
- If the work is clearly larger than a few days, propose a split into several specs and confirm it with the user before creating them.
- Create it: `specs/_kit/sdd new <feature|spike> "<title>"`, then claim the new ID with `specs/_kit/sdd claim <ID>`. Keep titles short and use the PLAN.md wording when the work comes from there.

## 3. Write the spec

Fill in every section of the template and delete the guidance comments.

- Frontmatter: `phase` (PLAN.md phase number), `depends_on` (spec IDs that must be done first), `refs` (the PLAN.md and BACKLOG.md items it delivers or settles, quoted strings), `updated` (today).
- Problem, scope in and out, user scenarios, numbered requirements in EARS form, and acceptance criteria that are observable and testable. Every requirement is covered by at least one acceptance criterion. No technology, schemas, or code.
- Never guess. Anything unknown becomes `[NEEDS CLARIFICATION: <specific question>]` in the section where it matters.

## 4. Clarify

- Collect the open markers. Ask the user the most important ones (up to five, those that change scope or behavior most) in a single AskUserQuestion call, with a recommended answer first where you have one.
- Write each answer into the spec where it belongs, and record it under Open questions as `Q: … A: … (date)`. Leave unanswered markers in place.
- Add a changelog line for what changed.

## 5. Finish

- Keep `status: draft`. Release the spec (`specs/_kit/sdd release <ID>`) and run `specs/_kit/sdd status --write`.
- Report in a few lines: the spec path, the number of open questions left, and the next step: the user reviews the spec, then runs `/sdd-plan <ID>`.
