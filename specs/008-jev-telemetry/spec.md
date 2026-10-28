---
id: 008
title: "Jev telemetry"
type: spike
status: done
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

## Why

<!-- The decision this spike unblocks, and the BACKLOG.md MVP items it settles. -->

Spike 007 made Jev a shadow provider in the MVP beside Laya: the engine's DecisionEngine adapter calls TypeSafe's hosted `/v1/systemone` API, and each answer is recorded next to the council and the human. Spike 006 settled telemetry for Claude Code, but nothing yet says what can be observed about the Jev calls themselves: what each response and the TypeSafe account expose (model version, tokens, cost, latency, errors, rate limits), how the engine records and correlates them with a run and step, and what the quality view can show from them over time.

This spike answers that, so that spike 7 (target architecture) can place Jev telemetry in the engine's telemetry ingestion, and so Jev's shadow data can be trusted and compared with Laya's. It extends the Telemetry pipeline item and the Laya MVP use item in `BACKLOG.md`.

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [x] **G1** What does each Jev response carry that can serve as telemetry: the body (model version, `usage`, confidence) and the HTTP headers (request ID, rate limits, timing), and what an error response looks like.
- [x] **G2** What TypeSafe offers outside the response: a usage or billing dashboard, a usage API or export, its granularity, and whether a request can carry our own identifiers (run, step, question) so the two can be joined.
- [x] **G3** How the engine records a Jev call: what the DecisionEngine adapter emits (OpenTelemetry spans and metrics, engine tables, or both), and how a call is joined to its run, checkpoint attempt, step, question, and provider version.
- [x] **G4** What Laya (`laya-serve`) exposes for the same purpose, so one provider-neutral telemetry shape covers both providers.
- [x] **G5** Failures: how timeouts, 4xx and 5xx errors, rate limits, and truncation show up, and how they map to *not evaluated* in the quality view.
- [x] **G6** Data handling: what TypeSafe retains of the state sent, for how long, and under which terms, and whether that stays acceptable beyond `sample-crm`.
- [x] **G7** Which measures the quality view can show from this telemetry: agreement over time, confidence, latency, cost per run, error rate, and model version changes.

## Briefing

<!-- Prepared by the spike-researcher agent: what the repository and current docs already say, with sources. -->

Prepared 2026-10-19 by the spike-researcher agent. Sources: INTENT = `docs/intent.md`; spike 006 and spike 007 = their `spec.md`; capture = `prototype/results/capture.json`. External sources were checked on 2026-10-19. The laya-serve facts come from reading the installed `laya` 0.4.12 source in spike 007's prototype venv (`laya/serve.py`, in the `next-available-spike-ec239e` worktree), not from a live capture.

What changes what the spike assumed:

- Jev does not truncate silently: an over-long request returns 400 `max_tokens_exceeded` (per the docs). Laya does truncate, and reports it in `usage.truncated`.
- laya-serve always returns `model: "laya-rl-agent"`. The checkpoint is only in `routing.model`, and the weights revision (SHA) only in `GET /health`, so the adapter must put Laya's version together.
- laya-serve has no OpenTelemetry, no `/metrics`, and no request ID. It sends `Server-Timing` and `X-Inference-Time-Ms` headers.
- TypeSafe documents no usage API or export. The console has a Usage page whose contents are undocumented.
- TypeSafe's Master Customer Agreement (MCA) lets TypeSafe keep and use derived "Telemetry" (logs, hashes, classifications, metrics) without restriction.

Already known in the repository:

- The DecisionEngine contract returns a label, the distribution, provider, model and version, latency, and raw metadata (INTENT, Typed decisions and Laya).
- Shadow results are logged with the state snapshot or hash, the driver's proposal, the engine decision, the human decision, and the later outcome (INTENT, Modes).
- Only the engine writes state (art. 1); *not evaluated*, *failed*, and *passed* stay distinct (art. 7). An unavailable provider, a timeout, or a failed call gives *not evaluated*, never *passed* (INTENT Promotion; art. 5).
- The adapter sends one question to several providers, records each result separately, maps `usage.truncated` to *not evaluated*, and only the engine holds the Jev key (spike 007, BACKLOG spike 7 inputs).
- The collector forwards Claude Code telemetry to the engine (spike 006). Nothing says whether the engine emits OTel for its own outbound calls. BACKLOG suggests one relational database with telemetry stored in it.
- Sending `sample-crm` to Jev is acceptable; any other repository needs a new decision (spike 007, Q5).
- The quality view shows evaluator agreement by provider and model (INTENT Quality monitoring); raw numbers and counts rather than significance (BACKLOG Comparison); Claude cost as an "API-equivalent estimate" (spike 006, Q5).

### Researched

**G1 · Jev API** ([API reference](https://docs.typesafe.ai/api.md), [Models](https://docs.typesafe.ai/models.md), [Confidence](https://docs.typesafe.ai/confidence.md))

- Documented errors: 400 (rule broken; `max_tokens_exceeded` over context), 401, 413 (body over 32 MiB), 415, 422, 429, 529 (overloaded). Request IDs, rate-limit headers, `traceparent`, and idempotency keys are not documented. The docs say 401 for a missing key, but the capture got 403, so the docs are incomplete on status codes.
- Limits: 64k tokens per request and 32k per question (state plus one question). The `TypeSafe-Danger-Zone-Long-Context: true` header raises both to 262k, at the same price but slower and less accurate.
- Aliases: `jev-latest` → `jev-1.13.0`; `jev-preview` → `jev-1.14.0` (since 2026-10-14). Aliases move without notice, so pin the versioned ID. `GET /v1/models` lists them.
- Confidence for `choice` is (p_max − 1/n)/(1 − 1/n), with no calibration claim. A `noul` answer has no confidence; |2p − 1| is the equivalent.
- SDKs are Python and JavaScript only, no Java. Both expose `request_id` from `x-typesafe-request-id` ([Python](https://docs.typesafe.ai/sdk/python/api/types/responses.md), [JS](https://docs.typesafe.ai/sdk/javascript/api/interfaces/RequestOptions.md)).

**G2 · Outside the response**

- Console `console.typesafe.ai`: Home, Playground, Usage, API Keys. A third-party guide (2026-10-09) says only that Usage "shows how many tokens you've consumed". No usage API or export is documented ([quickstart](https://docs.typesafe.ai/introduction/quickstart.md), [flaviocopes](https://flaviocopes.com/jev-api-key/)).
- Rate limits are per organisation, in tokens/s and requests/s, shown in the console. The same guide quotes 80 req/s and 100k tok/s, "adjusted dynamically" (not authoritative).
- Billing is prepaid, non-refundable credits expiring after 12 months or at the end of the term; "credit consumption rates can vary by model and account settings" (MCA §8.2). Price: $0.042 per million input tokens; output tokens are free ([MCA](https://typesafe.ai/legal/mca), 2026-09-23).

**G3, G7 · OpenTelemetry GenAI conventions** ([semantic-conventions-genai](https://github.com/open-telemetry/semantic-conventions-genai), main branch)

- Moved out of the main semconv repository; all `gen_ai.*` names are in Development status with no release, so they may change. `gen_ai.client.token.usage` and `gen_ai.client.operation.duration` are being replaced.
- Inference span `{gen_ai.operation.name} {gen_ai.request.model}`, kind CLIENT. Required: `gen_ai.operation.name`, `gen_ai.provider.name`. Conditionally required: `error.type`, `gen_ai.request.model`. Recommended: `gen_ai.response.model`, `gen_ai.response.id`, `gen_ai.usage.input_tokens`/`output_tokens`, `server.address`.
- Metrics: `gen_ai.client.inference.duration` (histogram, seconds), `gen_ai.client.inference.usage.input_tokens`/`output_tokens` (counters, recorded as 0 on failed calls).
- No well-known operation fits classification and no provider value fits TypeSafe or Laya; custom values (`typesafe`, `laya`) are allowed. Label, distribution, and confidence need our own namespace.

**G4 · laya-serve** (source of `laya` 0.4.12)

- Response: `model` always `"laya-rl-agent"`; `routing` gives the checkpoint (`model`) and a `reason`; `usage` adds `truncated`, `state_tokens`, `state_tokens_dropped`, `truncated_questions`; each answer has `answer_confidence` and `x_jev_confidence`. `LAYA_JEV_STRICT=1` strips these extras.
- Headers `Server-Timing: inference;dur=…` and `X-Inference-Time-Ms`. No request ID, no `/metrics`, no OpenTelemetry.
- `GET /health`: loaded checkpoints, their `revisions` (commit SHAs), the device, CPU-fallback counts. With `LAYA_API_KEY` set, an unauthenticated caller gets only `{"status":"ok"}`.
- Errors are FastAPI `{"detail": "<string>"}`: 401 bad or missing token; 413 state over 50,000 characters, over 64 questions, or body over 2 MiB; 422 bad model path or parameters; 500 "inference failed" (cause only in the server log); 503 with `Retry-After: 1` above `LAYA_MAX_CONCURRENT` (16).
- One inference worker thread, so requests are serialized. The 50,000-character cap is separate from the token window, so a large packet can get 413 before any truncation.

**G5 · Retries and timeouts** ([Python retries](https://docs.typesafe.ai/sdk/python/api/retries.md), [JS config](https://docs.typesafe.ai/sdk/javascript/api/interfaces/TypeSafeClientConfig.md))

- The Python SDK retries 408, 429, 5xx, connection errors, and timeouts: 2 retries, backoff 0.5 s doubling to 5 s, honouring `Retry-After`/`retry-after-ms`, in a 30 s total budget. The JS SDK times out each attempt after 10 s, with no total budget. The Java engine must do all of this itself.

**G6 · Data handling** ([Legal](https://docs.typesafe.ai/legal.md), [Privacy](https://typesafe.ai/legal/privacy-policy) 2025-11-19, [DPA](https://typesafe.ai/legal/data-processing) 2026-04-24, [MCA](https://typesafe.ai/legal/mca))

- Hosted in the US. No training on inputs (privacy policy; the MCA requires our consent). No fixed retention: "as long as reasonably necessary". Customer Data may be kept after the term for telemetry, abuse monitoring, and legal compliance; derived "Telemetry" may be used "without restriction". Zero data retention only on enterprise plans. The [subprocessor page](https://trust.typesafe.ai/subprocessors) rendered empty.

## Questions for you

<!-- Prepared by the agent, highest impact first; each says which goal questions it serves, why it matters, the options, and a suggested answer. Ticked when answered or skipped. -->

- [x] **Q1** What does your TypeSafe console show under Usage (granularity, per key, export), and which rate limits does it list for your organisation? (G2, G5, G7) If it shows only totals, the engine's own records are the only per-run source, and the limits set the retry design. *Suggested:* no default; a screenshot or a short description is enough.
- [x] **Q2** Where does the record of each provider call live? (G3, G4, G7) *Options:* an engine table only, one row per call / that table plus OTel `gen_ai.*` spans and metrics to the collector / OTel only. *Suggested:* an engine table only, with columns named after `gen_ai.*`: the engine is the caller and the sole authority, and exporting OTel only to ingest it back adds a loop and no data.
- [x] **Q3** Does the engine store the full state packet sent to each provider, or only a hash? (G3, G6) The full packet allows replaying the same question to a later model version. *Options:* full packet / hash plus references (commit SHA, evidence IDs) / both. *Suggested:* the full packet (up to about 25 KB per call), since the MVP is single-user and replay across versions is the main use of shadow data.
- [x] **Q4** Where is the provider model version pinned? (G1, G7) *Options:* in the definition (a new definition version per model upgrade, art. 7) / in engine or adapter config, recorded on every call. *Suggested:* engine config, recorded per call, because shadow mode influences nothing and comparing versions should not need a publish.
- [x] **Q5** Does the adapter retry a shadow call on 429, 529, 5xx, or a timeout? (G5) *Options:* no retry, record *not evaluated* / up to 2 retries with backoff, honouring `retry-after`, then *not evaluated* / retry later as engine work. *Suggested:* up to 2 retries, recording every attempt, as the TypeSafe SDKs do.
- [x] **Q6** What total deadline does one provider call get before it is *not evaluated*? (G5) Jev answers in about 0.3 s and Laya in 0.1–1.1 s. *Options:* 10 s / 30 s / longer. *Suggested:* 30 s across retries, matching the Python SDK.
- [x] **Q7** How does Jev's cost appear in the quality view? (G2, G7) *Options:* tokens only / tokens plus a computed cost labelled "list-price estimate" (price and date in config) / reconcile with the console by hand each month. *Suggested:* tokens plus a labelled estimate, as spike 006 decided for Claude.
- [x] **Q8** How is the "`sample-crm` only" rule for Jev enforced? (G6) Engine code must not know repositories (art. 3). *Options:* by convention / a deployment-config allow-list of subject keys per external provider, anything else *not evaluated* / a definition-level flag. *Suggested:* a deployment-config allow-list that fails closed.
- [x] **Q9** Do TypeSafe's terms (derived telemetry kept without restriction, no fixed retention, US hosting, no training on inputs, zero retention only on enterprise) change spike 007's Q5 answer? (G6) *Options:* keep `sample-crm` only / widen to other public repositories / stop sending code. *Suggested:* keep `sample-crm` only. (skipped: the user said not to worry about it; spike 007's decision stands)
- [x] **Q10** Which agreement measure does the quality view lead with for each provider? (G7) Spike 007 found exact band agreement at 2–4/10 for every evaluator, while other measures separated them. *Options:* exact band / within one band / good ranked above flawed / mean score distance / all, with counts. *Suggested:* within one band plus exact band, with run counts, per question and provider version.
- [x] **Q11** Does the engine get its own TypeSafe API key, separate from the prototype's? (G2, G6) *Suggested:* yes; it separates traffic in the console if usage is per key, and lets one key be revoked alone.
- [x] **Q12** Does laya-serve emit its own OpenTelemetry? (G4) *Options:* engine-side recording only / OTel auto-instrumentation of the FastAPI app / a thin wrapper. *Suggested:* engine-side recording only, plus reading `/health` revisions.

## Log

<!-- Append-only, oldest first: "- YYYY-MM-DD · <source> · <what was answered, added, or found>". -->

- 2026-10-19 · inbox (session that created the spike) · `prototype/capture.py` called Jev once per case (`https://api.typesafe.ai/v1/systemone`, key from the git-ignored `prototype/.env`) and saved status, headers (no cookies or auth), body, and timing to `prototype/results/capture.json`. Success with `jev-1.13.0` and with `jev-latest` (which resolved to `jev-1.13.0`): HTTP 200 in 255–271 ms; body `model`, `answers`, `usage` (`input_tokens`, `output_tokens`); headers include `x-typesafe-request-id` and Cloudflare's `CF-RAY`, with no rate-limit or timing headers. Every response, errors included, carries `x-typesafe-request-id`. An extra `metadata` field in the body is rejected (HTTP 400, `api_usage_error`, "Invalid request."), so client identifiers cannot ride in the body; that call also sent `X-Request-Id` and `traceparent` headers, so whether headers alone are accepted is untested. Unknown model: 400 `api_usage_error` "Unknown model: jev-0.0.0". Malformed question: 422 with a field-level `detail` list. No key: 403 `authentication_error`. Bad key: 401 `authentication_error`.
- 2026-10-19 · research (spike-researcher) · Jev returns 400 `max_tokens_exceeded` rather than truncating; limits 64k tokens per request, 32k per question; aliases move without notice; no usage API or export documented; price $0.042 per million input tokens, output free, prepaid credits; MCA lets TypeSafe use derived telemetry without restriction, no fixed retention, US hosting, no training on inputs. laya-serve 0.4.12 (from source): `model` always `laya-rl-agent`, version in `routing.model` and `/health` revisions, `Server-Timing` header, no request ID, no OTel. OTel `gen_ai.*` conventions are still in Development status. Details in Briefing.
- 2026-10-19 · answer to Q2 · An engine table only, one row per provider call, with columns named after the OTel `gen_ai.*` attributes; no OTel export of provider calls.
- 2026-10-19 · answer to Q3 · The engine stores the full state packet sent to each provider (up to about 25 KB per call), so the same question can be replayed against a later model version.
- 2026-10-19 · answer to Q4 · The provider model version is pinned in engine or adapter config, not in the definition, and recorded on every call.
- 2026-10-19 · answer to Q5 · On 429, 529, 5xx, or a timeout, the adapter retries up to 2 times with backoff, honouring `retry-after`, recording every attempt; then the call is *not evaluated*.
- 2026-10-19 · answer to Q6 · One provider call gets a 30 s total deadline across retries; past it the call is *not evaluated*.
- 2026-10-19 · answer to Q7 · The quality view shows Jev's tokens plus a computed cost labelled "list-price estimate" ($0.042 per million input tokens, output free), with the price and its date in config.
- 2026-10-19 · answer to Q8 · The "`sample-crm` only" rule is enforced by a deployment-config allow-list of subject keys per external provider; a call for any other subject is not sent and the step's result for that provider is *not evaluated*.
- 2026-10-19 · answer to Q9 · Skipped ("dont worry about this"): TypeSafe's terms do not reopen spike 007's decision; only `sample-crm` is sent to Jev.
- 2026-10-19 · answer to Q10 · The quality view leads with within-one-band and exact-band agreement, both with run counts, per question and per provider version.
- 2026-10-19 · answer to Q11 · The engine gets its own TypeSafe API key, separate from the prototype's.
- 2026-10-19 · answer to Q12 · laya-serve emits no OpenTelemetry; the engine records each Laya call itself and reads `/health` revisions.
- 2026-10-19 · note from the user · Run the researcher's extra captures now (Jev and laya-serve), without the 429 burst.
- 2026-10-19 · capture (`prototype/capture2.py`, `results/capture2.json`) · Jev: headers `X-Request-Id` and `traceparent` alone are accepted (200) but not echoed; only `x-typesafe-request-id` comes back. Token accounting: 1, 2, and 4 questions on the same state gave `input_tokens` 318, 330, 353 and `output_tokens` 31, 48, 81, so the state is counted once per request, not once per question. A 30,908-token state with one question passed (200, 488 ms). A state of about 37k tokens with one or two questions, and one of about 70k, gave 400 `{"detail": {"error_type": "max_tokens_exceeded"}}` with no `message`; nothing was truncated. A 50 ms client timeout gave no response and so no request ID. `jev-preview` answered as `jev-1.14.0`. `GET /v1/models` lists only the aliases `jev-latest` and `jev-preview`, with descriptions and release dates, not the versions they resolve to. No 429 burst was run.
- 2026-10-19 · capture (`prototype/capture_laya.py`, `results/capture_laya.json`) · laya-serve 0.4.12 from spike 007's venv on the M5 Pro, `LAYA_MODELS=multilingual`, a throwaway `LAYA_API_KEY` (fastapi 0.148.0, uvicorn 0.54.0, python-multipart added to that venv for this). Healthy 6.1 s after start. `/health` without the key: `{"status":"ok"}`; with it: `loaded`, `revisions` (multilingual `7b928d8…`), `device: mps`, `checkpoint_devices`, `cpu_fallbacks`. A request with no `model` auto-routed to the **english** checkpoint (`routing.model: english`, `repo: convaiinnovations/laya`, reason "English Latin text"), not the preloaded multilingual one, so it was loaded lazily: first call 637 ms, warm 25 ms, with `Server-Timing: inference;dur=…` and `X-Inference-Time-Ms`. A 47,000-character state was truncated on that checkpoint: 200 with `usage.truncated: true`, `state_tokens` 8841, `state_tokens_dropped` 8360, `input_tokens` 1024, `truncated_questions` both. Over 50,000 characters: 413 "state too large (54000 > 50000 chars)". Bad model: 422 listing the valid names and aliases. No key: 401 "invalid or missing bearer token". A burst of 24 concurrent calls: 16 × 200 and 8 × 503 "server busy, try again later" with `retry-after: 1`. `output_tokens` is always 0. The server log warns that the checkpoint ships invalid temperatures, so confidence from the affected entries is uncalibrated.
- 2026-10-19 · answer to Q1 (screenshot of the TypeSafe console Usage page) · Three hourly bar charts over about two days, each with a period total: Spend* ($0.0046), Tokens (112,613, stacked input and output), and Requests (35). No per-key breakdown, export, or rate limits were visible on the page.

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1

- **Fact** (capture 2): `input_tokens` counts the state once per request plus a little per question (318, 330, 353 for 1, 2, 4 questions), so asking several questions in one call is cheap.
- **Fact** (capture 2): request headers carry no telemetry back; the only response header of use is `x-typesafe-request-id`.

- **Decision** (Q4): the provider model version (for example `jev-1.13.0`, never an alias) is pinned in engine or adapter config, not in the definition, and recorded on every call. Comparing versions needs no definition publish.

- **Fact** (capture, `prototype/results/capture.json`): a success body has `model` (the resolved version: `jev-latest` came back as `jev-1.13.0`), `answers` keyed by question name, and `usage` with `input_tokens` and `output_tokens` (330 and 48 for a two-question call on a one-sentence state). A `choice` answer carries `choice`, `confidence`, and `probabilities` per label; a `noul` answer carries one number (`noul: 0.99`).
- **Fact** (capture): headers carry `x-typesafe-request-id` (`req_…`) on every response, errors included, and Cloudflare's `CF-RAY`. There are no rate-limit, quota, or server-timing headers.
- **Fact** (capture): latency from Lisbon was 255–271 ms for a success and 205–252 ms for an error.
- **Fact** (capture): errors are JSON `{"detail": {"error_type", "message"}}`, except 422, where `detail` is a list of field errors (`type`, `loc`, `msg`, `input`).

### G2

- **Fact** (Q1, console screenshot): the Usage page shows hourly spend, tokens (input and output), and requests for the organisation, with period totals. No per-key split, export, or rate limits were visible, and no usage API is documented. So the engine's call table is the only per-run, per-step source; the console is a coarse cross-check.
- **Fact** (Q1): $0.0046 for 112,613 tokens, almost all input, is about $0.041 per million, consistent with the $0.042 per million input-token price, so the engine's list-price estimate (Q7) should match the console's spend within rounding.
- Rate limits for the organisation were not shown; the third-party figure (80 req/s, 100k tok/s) stays unconfirmed, which does not matter at shadow-mode volume (one call per checkpoint attempt).

- **Fact** (capture 2): `X-Request-Id` and `traceparent` request headers are accepted but not echoed, and nothing documents that TypeSafe records them. Our identifiers cannot be joined to TypeSafe's side; the join key is TypeSafe's own `x-typesafe-request-id`, which the engine stores per call.
- **Fact** (capture 2): `GET /v1/models` lists aliases with descriptions and release dates but not the version each resolves to; the resolved version is only in each response's `model`.

- **Decision** (Q11): the engine gets its own TypeSafe API key, separate from the prototype's, so it can be revoked alone and its traffic told apart in the console if usage is shown per key.

- **Decision** (Q7): cost is computed by the engine from `input_tokens` × $0.042 per million (output tokens free), shown next to tokens as a "list-price estimate", with the price and its date in config. TypeSafe says credit rates "can vary by model and account settings" (MCA §8.2), so it stays an estimate.

- **Fact** (capture): an unknown body field (`metadata`) is rejected with 400 `api_usage_error` "Invalid request.", so our identifiers cannot travel in the body.
- Open: whether `X-Request-Id` or `traceparent` headers on their own are accepted, and whether TypeSafe records or echoes them.

### G3

- **Decision** (Q2): the engine records each provider call in its own table, one row per call (per attempt, see G5), with columns named after the OTel `gen_ai.*` attributes (`gen_ai.provider.name`, `gen_ai.request.model`, `gen_ai.response.model`, `gen_ai.response.id`, `gen_ai.usage.input_tokens`/`output_tokens`, `error.type`, duration). The engine does not export provider calls as OTel. Label, distribution, and confidence get their own columns.
- **Decision** (Q3): the row keeps the full state packet sent (up to about 25 KB), so a question can be replayed against a later model version.
- Join: each row carries the run, checkpoint attempt, step, typed question, provider, pinned and resolved model version, and attempt number, all set by the engine when it makes the call, so no external correlation is needed. TypeSafe's `x-typesafe-request-id` is stored to find a call on TypeSafe's side.
- **Fact** (OTel GenAI conventions): the `gen_ai.*` names are in Development status, so the column names follow them but do not depend on them.

### G4

- **Fact** (capture): a laya-serve success carries `model: "laya-rl-agent"` (fixed), `routing` (checkpoint `model`, `repo`, `reason`, language detection), `usage` (`input_tokens`, `output_tokens` always 0, `state_tokens`, `state_tokens_dropped`, `truncated`, `truncated_questions`), and per answer `probabilities`, `confidence` (normalized entropy), `answer_confidence`, and `x_jev_confidence` (Jev's formula). Headers: `Server-Timing: inference;dur=…` and `X-Inference-Time-Ms`; no request ID.
- **Fact** (capture): `/health` with the key gives `loaded`, `revisions` (commit SHA per loaded checkpoint), `device`, `checkpoint_devices`, and `cpu_fallbacks`; without the key only `{"status":"ok"}`.
- **Fact** (capture): with no `model`, laya-serve auto-routed English text to the `english` checkpoint (1024-token window), not the preloaded `multilingual` one that spike 007 chose. **Assumption** to confirm when the adapter is built: the adapter must always send `model: multilingual` explicitly, and read the revision for the checkpoint named in `routing.model` from `/health` after the call.
- **Fact** (capture): the server log warns that the checkpoint ships invalid temperatures, so some confidences are uncalibrated. The quality view should treat Laya's confidence as a raw signal, as it does Jev's (no calibration claim).
- So one provider-neutral row covers both: provider, requested model, resolved model (Jev's `model`; Laya's `routing.model` plus its `/health` revision), provider request ID (Jev only), input and output tokens, truncation (Laya only; Jev refuses instead), wall time and server inference time (Laya only), label, distribution, confidence as returned, status and error type, attempt number, and the full packet.

- **Decision** (Q12): laya-serve emits no OpenTelemetry. The engine records each Laya call in the same table as Jev's and reads the checkpoint revisions from `GET /health`.

### G5

- **Fact** (capture 2): Jev refuses an over-limit state with 400 `{"detail": {"error_type": "max_tokens_exceeded"}}` (no `message`), both over 32k tokens per question and over 64k per request; it never truncates. A 30.9k-token state passed.
- **Fact** (capture 2): a client timeout gives no response and no request ID, so the row records only the attempt, its duration, and `error.type: timeout`.
- **Fact** (laya capture): laya-serve truncates silently but reports it (`usage.truncated: true`, 200); a state over 50,000 characters gets 413; above 16 concurrent requests the excess gets 503 with `retry-after: 1`; bad model 422; bad key 401; inference failure 500 (cause only in the server log).
- Mapping to results: a 2xx with the answer → recorded answer. Laya `truncated: true` → *not evaluated* (spike 007). Jev `max_tokens_exceeded`, Laya 413, 401/403, 400/422 → *not evaluated* with no retry (they will not change on retry). 429, 503, 529, other 5xx, timeouts, connection errors → retried per Q5 within the Q6 deadline, then *not evaluated*. Every attempt is a row.
- Not tried: a real Jev 429 (burst skipped) or 529 / 5xx. The docs say 429 and 529 exist; the `retry-after` header on a Jev 429 is unconfirmed, so the adapter falls back to its own backoff when it is absent.

- **Decision** (Q6): one provider call has a 30 s total deadline across retries; past it the call is *not evaluated*.

- **Decision** (Q5): on 429, 529, 5xx, or a timeout, the adapter retries up to 2 times with backoff, honouring `retry-after`/`retry-after-ms`, and records every attempt; after that the call is *not evaluated*.

- **Fact** (capture): unknown model → 400 `api_usage_error`; malformed question → 422 with field errors; no key → 403 `authentication_error`; bad key → 401 `authentication_error`. Timeouts, 5xx, rate limiting (429), and truncation of a long state were not tried.

### G6

- **Fact** (TypeSafe privacy policy, DPA, MCA; see Briefing): hosted in the US; no training on inputs; no fixed retention ("as long as reasonably necessary"); Customer Data may be kept after the term for telemetry, abuse monitoring, and legal compliance; derived "Telemetry" (logs, hashes, classifications, metrics) may be used without restriction; zero data retention only on enterprise plans.
- **Decision** (Q9, skipped by the user): the terms do not change spike 007's decision. Only `sample-crm` is sent to Jev; any other repository still needs a new decision.
- **Decision** (Q8): the rule is enforced by a deployment-config allow-list of subject keys per external provider, failing closed: a subject not on the list is never sent, and that provider's result is *not evaluated*. Engine code knows no repository (art. 3).

### G7

- From the call table, per question, provider, and resolved model version, with run counts: within-one-band and exact-band agreement with the council and the human (Q10); confidence as returned (raw, no calibration claim for either provider); wall time, and Laya's server inference time; input tokens and Jev's list-price estimate per run (Q7); the rate of *not evaluated* calls split by cause (truncation, refused over-limit, auth, rate limit or overload, timeout); retries per call; and model version changes, shown as a marker where the resolved version changes.

- **Decision** (Q10): the quality view leads with within-one-band and exact-band agreement, both with run counts, per question and per provider version.

## Recommendation and decisions

<!-- Drafted when every goal question is answered, and agreed with the user. -->

| Decision | Choice | Alternatives considered | Why |
| --- | --- | --- | --- |
| Call record | One engine table, one row per provider call attempt, columns named after OTel `gen_ai.*` (provider, requested and resolved model, response ID, input and output tokens, `error.type`, duration) plus run, checkpoint attempt, step, question, attempt number, label, distribution, confidence, truncation, and server inference time | Table plus OTel export; OTel only | The engine is the caller and sole authority (art. 1); exporting only to ingest back adds a loop and no data. `gen_ai.*` is still in Development, so names follow it without depending on it (Q2) |
| State packet | Store the full packet sent with each call | Hash plus references; both | Replaying a question against a later model version is the main use of shadow data; about 25 KB per call is cheap for a single user (Q3) |
| Model version | Pinned in engine or adapter config, never an alias; the resolved version is recorded per call (Jev's `model`; Laya's `routing.model` plus its `/health` revision) | Pinned in the definition | Shadow mode influences nothing, so version comparisons need no definition publish (Q4). Aliases move without notice |
| Laya checkpoint | The adapter always sends `model: multilingual` | Let laya-serve auto-route | With no `model`, laya-serve routed English text to the 1024-token `english` checkpoint and truncated a large state (capture) |
| Failures | Retry 429, 503, 529, other 5xx, timeouts, and connection errors up to 2 times with backoff, honouring `retry-after`, within a 30 s total deadline; never retry 400, 401, 403, 413, 422; every attempt is a row; any call without an answer, and any Laya `truncated: true`, is *not evaluated* | No retry; retry later as engine work; 10 s or longer deadline | Fail closed (art. 5) while keeping shadow data complete; same policy as TypeSafe's SDKs (Q5, Q6) |
| Correlation with TypeSafe | Store `x-typesafe-request-id` per call; send no identifiers of our own | Send `X-Request-Id` or `traceparent` | TypeSafe accepts but neither echoes nor documents those headers, and rejects extra body fields (captures) |
| Cost | Tokens plus a computed "list-price estimate" ($0.042 per million input tokens, output free), price and date in config | Tokens only; monthly manual reconciliation | Consistent with spike 006 for Claude; the console's spend matched the estimate (Q1, Q7) |
| Usage outside the engine | The console's hourly charts are a coarse cross-check only; the engine gets its own TypeSafe key | Reuse the prototype key | No usage API or export; a separate key can be revoked alone (Q1, Q11) |
| Laya telemetry | No OpenTelemetry in laya-serve; the engine records Laya calls in the same table | Auto-instrument FastAPI; a wrapper | Everything needed is in the response, its headers, and `/health` (Q12) |
| External data rule | A deployment-config allow-list of subject keys per external provider, failing closed; only `sample-crm` is listed for Jev | By convention; a definition flag | Keeps repository knowledge out of engine code (art. 3) and enforces spike 007's decision, which TypeSafe's terms did not reopen (Q8, Q9) |
| Quality view | Per question, provider, and resolved version, with run counts: within-one-band and exact-band agreement first, then raw confidence, latency, tokens and cost estimate, *not evaluated* rate by cause, retries, and version-change markers | Exact band only; ranking; all measures equally | Spike 007 showed exact-band agreement alone does not separate evaluators (Q10) |

## Deliverables

- [x] Every goal question answered, and the recommendation agreed with the user
- [x] BACKLOG.md items settled by this spike updated

## Follow-ups

<!-- New specs, BACKLOG items, experiments, or open questions this spike created. -->

- For spike 7 (target architecture): place the provider call table in the engine's storage next to telemetry ingestion; DecisionEngine adapters write it, and the quality view reads it. No collector path for provider calls.
- For spike 8 (driver API contract): nothing; provider calls are engine-internal.
- Each follow-up is recorded in `BACKLOG.md`: Laya / Provider call telemetry, Quality view, and spike 7's inputs.
- For the Laya adapter (Phase 1): always send `model: multilingual`; read the revision for `routing.model` from `/health`; treat 503 with `retry-after` as retryable. Confirm when built.
- Unconfirmed: the shape of a real Jev 429 or 529, and whether it carries `retry-after`. The adapter falls back to its own backoff. Run the burst only if it matters later.
- Unconfirmed: the organisation's actual TypeSafe rate limits (not shown on the Usage page). Irrelevant at shadow-mode volume.

Things to try, proposed by the researcher (all Jev items reuse `prototype/capture.py`):

- Done 2026-10-19 (`capture2.py`, `capture_laya.py`): headers only, token accounting, over the context limit, client timeout, versions, and the laya-serve capture. See Log.
- Not run: burst for 429: about 150 tiny concurrent requests; record the 429 body and `retry-after` headers. Deliberately breaches the rate limit, so only with the user's go-ahead.

## Changelog

- 2026-10-19 Created.
- 2026-10-19 Inbox capture notes moved to Log and Findings; interview started.
- 2026-10-19 Briefing, Q1–Q12, and things to try added from the spike-researcher agent.
- 2026-10-19 Q1–Q12 answered (Q9 skipped); Jev and laya-serve captures run; all goal questions answered.
- 2026-10-19 Recommendation agreed with the user; spike released for verification.
- 2026-10-19 Verified and closed: BACKLOG.md (Laya / Provider call telemetry, Telemetry pipeline, Quality view, spikes 6b and 7) and PLAN.md updated.
- 2026-10-21 Note from spike 010: Laya is out of the MVP (Q27), so the Laya parts of these decisions wait until it returns; Jev's input now comes from a runner input packet job (Q26); typed decisions can be decision trees, one provider call per node (Q28).
