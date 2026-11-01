---
id: 018
title: "Architecture plant gaps"
type: feature
status: done
phase: 0
depends_on: [017]
refs: ["PLAN Phase 0 / Scaffolding / Engine skeleton"]
updated: 2026-11-01
---

# 018 · Architecture plant gaps

## Problem

Spec 017 proves each engine module rule fails when broken: one planted violation per row of `docs/engine/design.md` section 1, "How the rules are checked", run by the plant script in the required engine CI check. Its `/sdd-verify` review found three gaps.

1. The banned-name row is checked by two rules, one over class and package names and one over member names. The only plant breaks both and expects only the class-name rule's message. The member-name rule could stop working and no plant would notice.
2. The core-packages row is checked by two rules: allowed calls between core packages, and no cycles. The only plant proves the first. The cycle rule could stop working unnoticed.
3. The build pins JDK 25 whatever JDK runs the build tool. Nobody checked which JDK mutation testing runs the tests on, since it starts its own processes. If it uses the build tool's JDK, the mutation score could come from a different JDK than the one the engine ships on.

Closing them keeps spec 017's promise (AC-2, AC-3) honest before Phase 1 code starts relying on these rules.

## Scope

**In:**

- A planted violation that breaks only the member-name half of the banned-name rule, and is caught by that rule.
- A planted violation that makes a package cycle inside the core, caught by the cycle rule.
- Finding which JDK mutation testing runs the tests on when the build tool runs on another JDK, making it the pinned JDK if the tool allows it, and recording the result in the engine design.
- Listing the new plants in `engine/README.md`.

**Out:**

- New module rules or changes to what the existing rules allow: a later spec, if needed.
- Core packages that no call rule names. The allowed-calls rule only covers the nine named core packages, so a tenth package escapes it. Whether to forbid unnamed core packages goes to `BACKLOG.md` "Next".
- Pinning the JDK of any other tool the build runs beyond mutation testing.

## User scenarios

### S1 · A change silently breaks a module rule

A later spec edits the module rules and, by mistake, stops the member-name check or the cycle check from finding anything.

1. The plant script runs in the engine CI check.
2. The plant for that rule is no longer caught, so the check fails and names the plant.

### S2 · A developer builds on a machine whose default JDK is not 25

1. They run the documented build with every CI gate.
2. Mutation testing runs the tests on the pinned JDK, or the design says plainly which JDK it uses and why that is accepted.

## Requirements

- **R1** If a core or port class has a member whose name holds a banned word, and no class or package name does, then the engine build shall fail at the member-name rule.
- **R2** If core packages depend on each other in a cycle, then the engine build shall fail at the no-cycles rule.
- **R3** The plant script shall include one plant for R1 and one for R2, each passing only when the build fails with that rule's own message.
- **R4** Where the mutation testing tool can be pointed at the pinned JDK, the engine's mutation testing shall run the tests on that JDK, whatever JDK runs the build tool. Where it cannot, R5 records the JDK it uses and why that is accepted (Q1).
- **R5** `docs/engine/design.md` section 1 shall say which JDK mutation testing runs the tests on and how that is ensured.
- **R6** `engine/README.md` shall list the new plants with the rule each one proves.

## Acceptance criteria

- **AC-1** Given the member-name plant, when the plant script runs it, then the build fails with the member-name rule's message, and the plant has no banned word in any class or package name. (R1, R3)
- **AC-2** Given the cycle plant, when the plant script runs it, then the build fails with the no-cycles rule's message. (R2, R3)
- **AC-3** Given the full plant script, when it runs, then every plant, old and new, is caught and the clean build passes. (R3)
- **AC-4** Given a machine where the build tool runs on a JDK other than 25, when the build with every CI gate runs, then mutation testing runs the tests on JDK 25, or the design records that it cannot and which JDK it uses; shown by its log or by a recorded manual check. (R4)
- **AC-5** Given `docs/engine/design.md` section 1, when it is read, then it says which JDK mutation testing uses and how. (R5)
- **AC-6** Given `engine/README.md`, when it is read, then it lists both new plants with the rule each proves. (R6)

## Open questions

- Q1: If the mutation testing tool cannot be pointed at the pinned JDK, is recording the JDK it uses in the design enough? A: Pin it if the tool allows; otherwise record it (R4, R5). (owner, 2026-11-01)
- Q2: Should this spec go through plan, implement, and verify without stopping at each gate? A: Yes; this answer approves the spec and the plan. (owner, 2026-11-01)

## Changelog

- 2026-11-01 Created, from the gaps the spec 017 `/sdd-verify` review found. Noted while writing it: the allowed-calls rule lets no two named core packages call each other, so a cycle among them also breaks that rule; the cycle plant needs core packages the rule does not name.
- 2026-11-01 Q1 and Q2 answered: pin PIT's JDK if possible, else record it (R4); run the kit straight through.
- 2026-11-01 Approved (`/sdd-plan`; Q2).
- 2026-11-01 Planned: `plan.md` and `tasks.md` (5 tasks).
- 2026-11-01 Implementation started (`/sdd-implement`).
- 2026-11-01 Verified (`/sdd-verify`): AC-1 to AC-6 pass, AC-4 by a recorded manual check. Done.
