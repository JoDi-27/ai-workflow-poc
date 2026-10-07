---
id: {{ID}}
title: "{{TITLE}}"
type: spike
status: draft
phase: 0
depends_on: []
refs: []
updated: {{DATE}}
---

# {{ID}} · {{TITLE}}

<!--
A spike answers questions and settles decisions; it does not ship production code. Run it with /sdd-spike {{ID}}:
the spike-researcher agent prepares a briefing and questions, you answer and add information over as many sessions
as it takes, and everything learned is recorded here. Prototypes are throwaway and live under specs/{{ID}}-*/prototype/.
Progress counts the checkboxes in Goal questions, Questions for you, and Deliverables.
-->

## Inbox

<!-- Drop notes, links, constraints, or decisions here at any time, in any form. The next /sdd-spike session moves them into the Log and Findings and clears this section. -->

## Why

<!-- The decision this spike unblocks, and the BACKLOG.md MVP items it settles. -->

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [ ] **G1** ...

## Briefing

<!-- Prepared by the spike-researcher agent: what the repository and current docs already say, with sources. -->

## Questions for you

<!-- Prepared by the agent, highest impact first; each says which goal questions it serves, why it matters, the options, and a suggested answer. Ticked when answered or skipped. -->

## Log

<!-- Append-only, oldest first: "- YYYY-MM-DD · <source> · <what was answered, added, or found>". -->

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1

## Recommendation and decisions

<!-- Drafted when every goal question is answered, and agreed with the user. -->

| Decision | Choice | Alternatives considered | Why |
| --- | --- | --- | --- |

## Deliverables

- [ ] Every goal question answered, and the recommendation agreed with the user
- [ ] BACKLOG.md items settled by this spike updated

## Follow-ups

<!-- New specs, BACKLOG items, experiments, or open questions this spike created. -->

## Changelog

- {{DATE}} Created.
