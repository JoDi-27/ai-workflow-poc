---
id: 008
title: "Jev telemetry"
type: spike
status: draft
phase: 0
depends_on: [006, 007]
refs: ["PLAN Phase 0 / Spikes / 6b. Jev telemetry", "BACKLOG Spikes / Wave 2 / 6b. Jev telemetry", "BACKLOG MVP / Telemetry pipeline", "BACKLOG MVP / Laya / MVP use"]
updated: 2026-10-19
---

# 008 · Jev telemetry

<!--
A spike answers questions and settles decisions; it does not ship production code. Run it with /sdd-spike 008:
the spike-researcher agent prepares a briefing and questions, you answer and add information over as many sessions
as it takes, and everything learned is recorded here. Prototypes are throwaway and live under specs/008-*/prototype/.
Progress counts the checkboxes in Goal questions, Questions for you, and Deliverables.
-->

## Inbox

<!-- Drop notes, links, constraints, or decisions here at any time, in any form. The next /sdd-spike session moves them into the Log and Findings and clears this section. -->

- 2026-10-19, from the session that created this spike (before the interview, which the user wants run in its own session): `prototype/capture.py` called Jev once per case and saved status, headers (minus cookies and auth), body, and timing to `prototype/results/capture.json`. The key is read from the git-ignored `prototype/.env`. Raw findings, not yet in Log or Findings:
  - Success (`jev-1.13.0`, and `jev-latest`, which resolved to `jev-1.13.0`): HTTP 200 in 255–271 ms; body `model`, `answers`, `usage` (`input_tokens`, `output_tokens`); headers include `x-typesafe-request-id` and Cloudflare's `CF-RAY`, with no rate-limit or timing headers.
  - Every response, errors included, carries `x-typesafe-request-id`.
  - An extra `metadata` field in the body is rejected (HTTP 400, `api_usage_error`, "Invalid request."), so client identifiers cannot ride in the body. The same call also sent `X-Request-Id` and `traceparent` headers, so whether headers are accepted on their own is still untested.
  - Unknown model: 400 `api_usage_error` "Unknown model: jev-0.0.0". Malformed question: 422 with a field-level `detail` list. No key: 403 `authentication_error`. Bad key: 401 `authentication_error`.

## Why

<!-- The decision this spike unblocks, and the BACKLOG.md MVP items it settles. -->

Spike 007 made Jev a shadow provider in the MVP beside Laya: the engine's DecisionEngine adapter calls TypeSafe's hosted `/v1/systemone` API, and each answer is recorded next to the council and the human. Spike 006 settled telemetry for Claude Code, but nothing yet says what can be observed about the Jev calls themselves: what each response and the TypeSafe account expose (model version, tokens, cost, latency, errors, rate limits), how the engine records and correlates them with a run and step, and what the quality view can show from them over time.

This spike answers that, so that spike 7 (target architecture) can place Jev telemetry in the engine's telemetry ingestion, and so Jev's shadow data can be trusted and compared with Laya's. It extends the Telemetry pipeline item and the Laya MVP use item in `BACKLOG.md`.

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [ ] **G1** What does each Jev response carry that can serve as telemetry: the body (model version, `usage`, confidence) and the HTTP headers (request ID, rate limits, timing), and what an error response looks like.
- [ ] **G2** What TypeSafe offers outside the response: a usage or billing dashboard, a usage API or export, its granularity, and whether a request can carry our own identifiers (run, step, question) so the two can be joined.
- [ ] **G3** How the engine records a Jev call: what the DecisionEngine adapter emits (OpenTelemetry spans and metrics, engine tables, or both), and how a call is joined to its run, checkpoint attempt, step, question, and provider version.
- [ ] **G4** What Laya (`laya-serve`) exposes for the same purpose, so one provider-neutral telemetry shape covers both providers.
- [ ] **G5** Failures: how timeouts, 4xx and 5xx errors, rate limits, and truncation show up, and how they map to *not evaluated* in the quality view.
- [ ] **G6** Data handling: what TypeSafe retains of the state sent, for how long, and under which terms, and whether that stays acceptable beyond `sample-crm`.
- [ ] **G7** Which measures the quality view can show from this telemetry: agreement over time, confidence, latency, cost per run, error rate, and model version changes.

## Briefing

<!-- Prepared by the spike-researcher agent: what the repository and current docs already say, with sources. -->

## Questions for you

<!-- Prepared by the agent, highest impact first; each says which goal questions it serves, why it matters, the options, and a suggested answer. Ticked when answered or skipped. -->

## Log

<!-- Append-only, oldest first: "- YYYY-MM-DD · <source> · <what was answered, added, or found>". -->

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1

### G2

### G3

### G4

### G5

### G6

### G7

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

- 2026-10-19 Created.
