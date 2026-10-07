---
id: {{ID}}
title: "{{TITLE}}"
type: feature
status: draft
phase:
depends_on: []
refs: []
updated: {{DATE}}
---

# {{ID}} · {{TITLE}}

<!--
spec.md says WHAT and WHY. No technology, schemas, class names, or code: those go in plan.md.
Write anything unknown as a NEEDS CLARIFICATION marker, for example:
  - [NEEDS CLARIFICATION: can a run have more than one owner?]
and never guess. /sdd-plan refuses to start while markers remain.
Delete these comments once the section is filled in.
-->

## Problem

<!-- Why this matters now, who it is for, and which happy path step (BACKLOG.md) or PLAN.md item it moves forward. -->

## Scope

**In:**

**Out:**
<!-- Each "out" item says where it goes instead: BACKLOG.md "Next", or another spec. -->

## User scenarios

<!-- The main flows, from the actor's point of view. Number them so requirements can refer to them. -->

### S1 · <name>

<actor> wants to <goal>, so that <outcome>.

1. ...

## Requirements

<!--
Numbered, one behavior each, in EARS form:
  Ubiquitous:   The <system> shall <response>.
  Event:        When <trigger>, the <system> shall <response>.
  State:        While <state>, the <system> shall <response>.
  Unwanted:     If <condition>, then the <system> shall <response>.
-->

- **R1** The <system> shall ...

## Acceptance criteria

<!-- Observable and testable. Every requirement is covered by at least one. /sdd-verify checks each one and records evidence in tasks.md. -->

- **AC-1** Given ..., when ..., then ...

## Open questions

<!-- Resolved questions stay here with their answer: "Q: ... A: ... (decided by, date)". -->

## Changelog

- {{DATE}} Created.
