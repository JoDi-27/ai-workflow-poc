---
id: 007
title: "Laya/Jev role in the workflow"
type: spike
status: done
phase: 0
depends_on: [001, 002, 003]
refs: ["PLAN Phase 0 / Spikes / 6. Laya/Jev role in the workflow", "BACKLOG Spikes / Wave 2 / 6. Laya/Jev role in the workflow", "BACKLOG MVP / Laya / Runtime and serving", "BACKLOG MVP / Laya / MVP use", "BACKLOG MVP / Laya / Model checkpoint", "BACKLOG MVP / Laya / Hardware"]
updated: 2026-10-19
---

# 007 · Laya/Jev role in the workflow

<!--
A spike answers questions and settles decisions; it does not ship production code. Run it with /sdd-spike 007:
the spike-researcher agent prepares a briefing and questions, you answer and add information over as many sessions
as it takes, and everything learned is recorded here. Prototypes are throwaway and live under specs/007-*/prototype/.
Progress counts the checkboxes in Goal questions, Questions for you, and Deliverables.
-->

## Inbox

<!-- Drop notes, links, constraints, or decisions here at any time, in any form. The next /sdd-spike session moves them into the Log and Findings and clears this section. -->

## Why

Laya is part of the MVP, but where a typed decision model actually adds value has not been worked out. The Laya items in `BACKLOG.md` (Runtime and serving, MVP use, Model checkpoint, Hardware) carry suggested defaults that are placeholders until this spike. Spike 003 put one shadow-mode typed question, `code_quality_band`, on the `code_quality` step; this spike confirms or changes it, tries it on a few local test-bed cases, and recommends the order of later roles.

It settles all Laya items in `BACKLOG.md`. Spikes 7 (target architecture) and 9 (technology stack, Laya serving) depend on it.

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [x] **G1** What Laya and Jev are in practice: inputs, outputs, confidence semantics, input limits, runtime and hardware needs, licensing, and availability (local or hosted).
- [x] **G2** Which roles are worth it: human step assist, typed step evaluation, transition guidance, and run categorization for the feedback loop.
- [x] **G3** Where in the reference workflow each role fits, and how it relates to the Claude evaluator and the human at the same point.
- [x] **G4** Which typed questions and label sets to start with (confirm or change spike 003's `code_quality_band`), and how to label the first local cases: hand-made good and flawed variants of test-bed tasks T01, T03, T04, T06, and T11 (ten cases), labeled by the user blind to the Claude evaluator. Token-count a case packet first, since `laya-typed-decisions` has a 1024-token context and the sandbox is Spring Boot.
- [x] **G5** Whether Laya and Jev overlap or complement each other.
- [x] **G6** Trial: Laya on a few local cases, comparing the general English and typed-decisions English checkpoints, with latency measured on the M5 Pro.

## Briefing

<!-- Prepared by the spike-researcher agent: what the repository and current docs already say, with sources. -->

Prepared 2026-10-19. Sources: INTENT = `WORKFLOW_ENGINE_INTENT.md`; spike 002 = `specs/002-*/spec.md`; spike 003 = `specs/003-*/spec.md`. External sources were checked on 2026-10-19.

Findings that go against what the spike assumed:

- `laya-typed-decisions` was fine-tuned on four non-code domains: agent traces, customer service, invoices, and security incidents. Zero-shot, the base checkpoints are near chance on typed decisions: 0.36 accuracy, against 0.32 random and 0.46 for always picking the most common label. Expect `code_quality_band` to be near chance without fine-tuning.
- No full diff of the five case tasks fits in 1024 tokens. Laya silently drops the end of an over-long state, and test files sort after `src/main`, so they are cut first. T01's flaw is a missing test, so it would vanish without a warning.
- Jev is hosted only (TypeSafe, early access from a waitlist). It has a 64k context, and its wire format is the same as `laya-serve`'s.

### G1 · What Laya and Jev are

- The engine is the only caller. A `DecisionEngine` takes a bounded state packet, a named typed question, a version, and the permitted labels. It returns a label, a distribution, the provider, model, and version, latency, and raw metadata. A failure, timeout, or outage leaves the step *not evaluated*. (INTENT, Typed decisions and Laya; constitution art. 5)
- **Laya:** version 0.4.12 on PyPI; code and weights are Apache-2.0, by Convai Innovations. It needs Python 3.10+ and PyTorch, and picks a device in the order CUDA, MPS, XPU, CPU; MLX is not mentioned. ([GitHub](https://github.com/NandhaKishorM/laya), [PyPI](https://pypi.org/project/laya/))
- **Laya checkpoints:**
  - `laya`: ModernBERT-large, 421M parameters, 512 tokens.
  - `laya-typed-decisions`: 421M parameters, 1024 tokens, F16 safetensors, no ONNX export. ([model card](https://huggingface.co/convaiinnovations/laya-typed-decisions))
  - `laya-multilingual`: mmBERT, 322M parameters, 1024 tokens by default and settable up to 8192; weaker on English, with no fitted temperatures.
- **Question types:** `choice`, `score`, and `noul`. `score` is the weakest. Labels such as `yes`/`no` are followed instead of their descriptions, and negation is unsafe. (README)
- **Input limit:** the state budget is `max_len − head_len − 1`. A longer state is truncated to its first window without a warning. `predict_long` windows the state, but returns the most confident window, "not calibrated for the whole document". (README)
- **Confidence:** `confidence` is 1 minus the normalised entropy; the README recommends `answer_confidence` for gating. The shipped checkpoints are over-confident: `laya-typed-decisions` has an ECE of 0.213. Fitting one temperature per question type and option count brings `laya` from about 0.47 to about 0.08. (README; model card)
- **Serving:** `laya-serve` is a FastAPI service with `/v1/systemone` and `/batch`, and `model` set to `english`, `multilingual`, or `typed-decisions`. It is self-hosted only (Docker, Nix). About 37 ms per question on Apple-silicon MPS, about 13 s to compile on the first run, and a weights download of about 2.3 GB. `laya-java` (ONNX inside the JVM) is pre-release: `0.1.0-SNAPSHOT`, not on Maven Central. (README)
- **Jev:** TypeSafe's hosted, closed "System One" model, in early access from a waitlist; no self-hosting. `jev-1.13.0` is stable, and `jev-1.14.0` has been in preview since 2026-10-14. The context is 64k per request and 32k per question; some third-party pages say 32k in total. It costs $0.042 per million input tokens (output is free) and takes about 70–500 ms. `score` takes 2–10 levels and `choice` up to 255 options. Its confidence makes no calibration claim. Known weaknesses: a bias toward the first option and distraction by irrelevant state. ([blog](https://typesafe.ai/blog/introducing-system-one-models-and-jev), [docs](https://docs.typesafe.ai))
- The M5 Pro with 64 GB is the experiment machine. Containers on macOS generally cannot use the Apple GPU. (INTENT, Hosting; BACKLOG Local cluster)

### G2 · Which roles are worth it

- There are four roles: human step assist, typed step evaluation, transition guidance, and (later) run categorization. The modes are shadow, advisory, and bounded control, and moving between modes is a human decision based on evidence. (INTENT, Roles, Modes, Promotion)
- Model output may inform a human step, but never satisfies or removes it. (constitution art. 2)
- The MVP is shadow only. (spike 003 Q1; `docs/definitions/schema.md`)

### G3 · Where each role fits

- `code_quality_band` is a `typed_decisions` block on the `code_quality` council-of-3 runner evaluator. It is not in the ordered steps, earns no points, and never blocks. Its result is recorded next to the council's median band and the human decision. (`docs/definitions/sdlc-reference.yaml`, `example-run.md`)
- In advisory mode, the output is never shown to a fresh-context evaluator, to avoid anchoring it. (INTENT, Modes)
- Transition guidance fits only where several transitions are permitted. The reference workflow has one such point: `verification`, with `approve_release`, `rework`, and `discard`.
- Human step assist would sit on `release_approval`. T11's injection risk is the planted case that a human should catch. (`docs/test-bed.md`)

### G4 · Typed questions, labels, and labelling

- `code_quality_band` has the labels `[poor, weak, good, exemplary]` (poor 1–3, weak 4–5, good 6–8, exemplary 9–10; passing is 6 or more) and the inputs `[requirements_doc, diff]`. Schema rule 12 makes the labels equal the step's band IDs.
- The ten cases are a good and a flawed variant each of T01, T03, T04, T06, and T11, labelled by the user without seeing the Claude evaluator's output. The `sample-crm` baseline (spec 004) is not built yet, so no real diffs exist.
- Token estimate, still to measure:
  - The question with its four band descriptors takes about 70–90 tokens, and `requirements_doc` about 100–180. That leaves about 750–830 tokens, or roughly 60–70 diff lines.
  - Full diffs with tests and 3 lines of context: T01 about 1,000–1,200 tokens, T11 about 1,000–1,500, T06 about 1,200–1,700, and T03 and T04 about 1,800–3,000 each. None of them fit.
  - Compacted to main code only, with no context lines (`-U0`): T01, T06, and T11 (about 400–550 tokens) fit. T03 and T04 (about 900–1,400) probably do not.

### G5 · Laya and Jev

- Jev is a later provider behind the same contract, compared on the same cases. (INTENT, Hosting; PLAN phase 9)
- `laya-serve` copies Jev's `/v1/systemone` request and response format. Both offer `choice`, `score`, and `noul`.
- Third-party figures in the Laya README:
  - Typed-decisions accuracy: Laya 0.766, Jev 0.727.
  - ECE after temperature fitting: Laya 0.081, Jev 0.246.
  - Typical (p50) latency: Laya about 33 ms, Jev 236–276 ms.
  - Jev leads with more than 20 options, on soft accuracy, and on raw calibration.
- So they mostly complement each other: Laya is local, fast, short-context, and can be tuned; Jev is hosted, long-context, and closed.

### G6 · Trial

- The plan is to compare the general English and typed-decisions checkpoints on local cases, check sensitivity to input length, wording, and label order, and measure latency on the host. (INTENT, Promotion; BACKLOG Model checkpoint, Hardware)
- A handful of cases is enough to compare checkpoints, but not to calibrate them. (spike 002, Researched)

## Questions for you

<!-- Prepared by the agent, highest impact first; each says which goal questions it serves, why it matters, the options, and a suggested answer. Ticked when answered or skipped. -->

- [x] **Q1** (G2, G4, G6) What must Laya prove in the MVP? *Why:* the typed-decisions checkpoint is near chance on code without fine-tuning. If a useful signal is required, fine-tuning or a different question becomes MVP scope. *Options:* the contract and shadow logging work end to end, whatever the accuracy / a signal better than the most-common-label baseline / both, with the bar set after the trial. *Suggested:* the plumbing plus an honest agreement number, since shadow mode influences nothing.
- [x] **Q2** (G4, G6) What should Laya's packet for `code_quality` contain? *Why:* full diffs overflow 1024 tokens, and the truncation is silent. *Options:* (a) requirements plus a compacted main-code diff (`-U0`, no tests) / (b) the full packet through `predict_long` windows / (c) `laya-multilingual` at `max_len` 8192 / (d) a summary written by the driver or the evaluator. *Suggested:* (a), with the adapter returning *not evaluated* instead of truncating.
- [x] **Q3** (G2, G4) Should `code_quality_band` stay a 4-band `choice`, or become narrower `noul` risk checks? *Why:* a classifier is weak at whole-quality judgments, and narrow risk checks such as "SQL is built from request input" suit it better. *Options:* keep `choice` / use `score` / add or replace it with 1–3 `noul` checks. *Suggested:* keep `choice` for the MVP, and trial one `noul` risk check on T11 beside it.
- [x] **Q4** (G1, G5, G6) Do you have Jev early access? *Options:* yes / waitlisted / no. *Suggested:* leave Jev out of the MVP either way; the shared wire format makes it cheap to add later.
- [x] **Q5** (G5) Is it acceptable to send `sample-crm` diffs to TypeSafe's hosted API in the US? *Options:* yes, for `sample-crm` only / no third-party model APIs. *Suggested:* yes for `sample-crm`, since it is public.
- [x] **Q6** (G1; feeds spikes 9, 10) Is a Python `laya-serve` running natively on the Mac, outside the cluster, acceptable for the MVP? *Options:* native host service on MPS / CPU in the cluster / `laya-java` ONNX inside the engine. *Suggested:* native `laya-serve`, the most mature path, and it keeps the Apple GPU.
- [x] **Q7** (G2, G4) Is fine-tuning Laya on your own labelled cases ever in scope? *Why:* it is the only route to a useful code-review signal, and the shipped fine-tune used 6,000 decisions. *Options:* never / after the MVP, once real runs supply labels / now. *Suggested:* after the MVP, recorded in BACKLOG "Next".
- [x] **Q8** (G2, G3) In what order should the later roles come? *Suggested:* human step assist (a `noul` risk flag on `release_approval`), then run categorization, then transition guidance (there is only one branch point), with typed step evaluation last.
- [x] **Q9** (G4) Who writes the ten hand-made variants? *Options:* Claude drafts each in a separate session and you review the code / you write them. *Suggested:* Claude drafts and you review, then you label later without seeing any evaluator output.
- [x] **Q10** (G4) Label each case with a band, or with a raw 1–10 score? *Suggested:* a raw score, since the band follows from it and it can be compared with the council median.
- [x] **Q11** (G4, G6) Wait for the `sample-crm` baseline (spec 004), or count tokens now on a throwaway mini Spring Boot project? *Suggested:* count tokens now on a mini project, and run the full ten-case trial after spec 004.
- [x] **Q12** (G4, G6) Include `laya-multilingual` at `max_len` 8192 as a third checkpoint in the trial? *Suggested:* yes, since it is the only Laya checkpoint that fits whole diffs, and it adds only minutes.

## Log

<!-- Append-only, oldest first: "- YYYY-MM-DD · <source> · <what was answered, added, or found>". -->

- 2026-10-19 · research · Briefing prepared. Key facts: `laya-typed-decisions` was trained on non-code domains and is near chance zero-shot; full case diffs overflow 1024 tokens and are truncated silently; Jev is hosted only, with a 64k context and the same wire format.
- 2026-10-19 · answer to Q1 · The MVP bar for Laya is the plumbing plus an honest number: the contract and shadow logging work end to end, and agreement with the human and the council is recorded, whatever it is.
- 2026-10-19 · answer to Q3 · Keep `code_quality_band` as a 4-band `choice` question, and trial one `noul` risk check on T11 beside it.
- 2026-10-19 · answer to Q4 · The user has Jev early access, so the trial can run the same packets on `jev-1.13.0`.
- 2026-10-19 · Q2 · The user did not follow the packet question; it was explained again in plain terms.
- 2026-10-19 · answer to Q2 · Use the bigger-window model: `laya-multilingual` with `max_len` up to 8192, so that whole diffs fit, accepting weaker English and no fitted temperatures. This also answers Q12: the trial includes `laya-multilingual`.
- 2026-10-19 · answer to Q5 · Sending `sample-crm` code and diffs to TypeSafe's hosted Jev API is acceptable; other repositories need a new decision.
- 2026-10-19 · answer to Q6 · The user said: "if we use laya in some other env that has no mac, we need a deployable solution for it. for testing, my mac can be used". So Laya serving must be deployable without a Mac (for example a container on CPU or GPU on GKE), and the Mac may run it natively for local testing.
- 2026-10-19 · answer to Q7 · Fine-tuning Laya on our own labelled cases comes after the MVP, once real runs supply labels; it goes to BACKLOG "Next".
- 2026-10-19 · answer to Q8 · The order of later roles: human step assist (narrow risk flags at `release_approval`), then run categorization, then transition guidance, with typed step evaluation last.
- 2026-10-19 · answer to Q9 · Claude drafts the ten variants, each in a session separate from any evaluation; the user reviews the code, then labels the cases without seeing any evaluator output.
- 2026-10-19 · answer to Q11 · The user pointed out that spec 004 is built. Confirmed: `sample-crm` has the tag `baseline` at `0916dbf` (`docs/test-bed.md`), so the token count and the trial use the real baseline, with no mini project. This worktree was brought up to date with `main` to get spec 004 and spec 006.
- 2026-10-19 · answer to Q10 · The user labels each case with a raw 1–10 score; the band follows from it.
- 2026-10-19 · note from the user · Approved downloading the `laya` package from PyPI into a git-ignored venv under `prototype/`, and the `laya-typed-decisions` and `laya-multilingual` checkpoints from Hugging Face. For Jev, the user sets the API key in an environment variable, which the trial script reads and never prints.
- 2026-10-19 · research · Installed `laya` 0.4.12 in `prototype/.venv` and downloaded `laya`, `laya-typed-decisions`, and `laya-multilingual`. Ran `prototype/probe.py` on the M5 Pro (MPS) with a synthetic diff of 40 and 400 lines:
  - `predict` returns `usage.truncated`, `state_tokens`, and `state_tokens_dropped`, so truncation is reported at the library API; it is only silent to a caller that ignores `usage`. This corrects the briefing.
  - The effective state window was about 430 tokens for `laya` (273 of 707 dropped), about 945 for `laya-typed-decisions`, and about 945 for `laya-multilingual` at its default; at `max_len=8192`, `laya-multilingual` took all 7,224 tokens.
  - A diff line of Java cost about 17 tokens, more than the briefing's estimate of about 12, so the 1024 checkpoints hold only about 50 diff lines.
  - Warm latency per question: 50–150 ms for every checkpoint at about 700 tokens, and 1.1 s for `laya-multilingual` at about 7,200 tokens. Loading took 0.3–6 s; the first call took up to 1.5 s.
  - On the same meaningless repeated line, `laya-typed-decisions` was near uniform (`good`, 0.29), and `laya-multilingual` said `poor` at 0.92 `answer_confidence`: a first sign that the uncalibrated multilingual checkpoint is over-confident.
- 2026-10-19 · research · The [laya-case-variants session](#local_ce7a9c2f-a81e-444a-95aa-2ed93d464d1c) wrote the ten variants to `prototype/cases/`: patches against `baseline`, five requirements docs, and a README. All ten apply cleanly to `baseline` (checked again here) and pass `./mvnw -B verify`; nothing was scored or evaluated. The local `sample-crm` clone was not touched.
- 2026-10-19 · research · `prototype/tokens.py` counted each full packet (requirements plus the full diff, tests included) with each checkpoint's tokenizer. `laya-typed-decisions`: 1,172 (T01 flawed) to 5,222 (T04 good). `laya-multilingual`: 1,315 to 6,666. No packet fits in 1024; all ten fit in 8192 with room for the question head.
- 2026-10-19 · note from the user · Asked for an easier way to read the patches; `prototype/review.py` builds `prototype/review.html`, a local side-by-side diff page (diff2html from jsDelivr) with no model output on it.
- 2026-10-19 · answer (labels) · The user's blind scores, given before any model output was seen, are now in `prototype/cases/labels.json`: T01 good 8, T01 flawed 4, T03 good 7, T03 flawed 4, T04 good 5 ("not tested"), T04 flawed 3, T06 good 9, T06 flawed 1, T11 good 7, T11 flawed 1. As bands: good, weak, good, weak, weak, poor, exemplary, poor, good, poor.
- 2026-10-19 · research · Trial (`prototype/trial.py`, raw results in `prototype/results/laya.json`), run after the labels were in. `code_quality_band` used the rubric of `sdlc-reference.yaml`, asked in two band orders (ascending and descending), on the full packets. Results:

  | Checkpoint | Order | Exact band | Within one band | Good ranked above flawed | Truncated | Mean `answer_confidence` | p50 latency |
  | --- | --- | --- | --- | --- | --- | --- | --- |
  | `laya` (512) | asc | 3/10 | 7/10 | 0/5 | 10/10 | 0.51 | 76 ms |
  | `laya` (512) | desc | 3/10 | 7/10 | 0/5 | 10/10 | 0.49 | 75 ms |
  | `laya-typed-decisions` (1024) | asc | 2/10 | 5/10 | 3/5 | 10/10 | 0.36 | 153 ms |
  | `laya-typed-decisions` (1024) | desc | 3/10 | 6/10 | 2/5 | 10/10 | 0.34 | 152 ms |
  | `laya-multilingual` (8192) | asc | 3/10 | 7/10 | 3/5 | 0/10 | 0.40 | 534 ms |
  | `laya-multilingual` (8192) | desc | 1/10 | 5/10 | 1/5 | 0/10 | 0.41 | 304 ms |

  The reference: always answering one of the three most common human bands scores 3/10 exact, and chance ranks 2.5 of 5 pairs. `laya` answered `good` every time. Reversing the band order changed 3 of 10 answers on `laya-multilingual` and 4 of 10 on `laya-typed-decisions` (position bias). The `noul` check "SQL is built from request input" did not separate the T11 pair: P(true) was 0.835 for the good variant and 0.82 for the flawed one on `laya-multilingual`, and 0.50–0.52 (truncated) on the others.
- 2026-10-19 · research · Jev trial (`prototype/jev_trial.py`, raw results in `prototype/results/jev.json`): `jev-1.13.0` on `https://api.typesafe.ai/v1/systemone`, with the same packets and questions (shared in `prototype/trial_common.py`), the key read from the git-ignored `prototype/.env`. Results, ascending and then descending order:
  - Exact band 3/10 and 3/10; within one band 8/10 and 8/10; good ranked above flawed 4/5 and 5/5; nothing truncated; mean top probability 0.58 and 0.59; p50 latency 308 and 353 ms.
  - Answers were identical in both orders (no position bias on these cases): exemplary, weak, exemplary, good, good, good, exemplary, weak, good, good. Against the human bands (good, weak, good, weak, weak, poor, exemplary, poor, good, poor), Jev is lenient: it puts every case one band higher or the same, except T11 flawed (good against poor).
  - The `noul` check "SQL is built from request input" separated the T11 pair cleanly: P(true) 0.06 for the good variant and 0.98 for the flawed one.
  - 66,788 input tokens in total for 21 questions, about $0.003 at $0.042 per million.
- 2026-10-19 · research (after closing, at the user's question on T03-good) · Why `laya-multilingual` said `poor` where Jev said `exemplary`: Laya gave T03-good and T03-flawed nearly the same distribution (poor 0.40 against 0.37), while Jev separated them (exemplary 0.72 against good 0.37). Ablations on T03 with `laya-multilingual` at 8192: requirements only, no diff → `good` 0.83; "(no changes)" → `good` 0.57; full packet → `poor` 0.40; main code without tests → `poor` 0.42; diff without requirements → `poor` 0.43; lorem ipsum in place of the diff → `poor` 0.75. So Laya rates an empty change above the real good one and reacts to the length and unfamiliarity of the state, not to whether the code meets the requirements. Jev reads the code (it drops a band for the planted flaw) but rates one band high.
- 2026-10-19 · research (after closing, at the user's request) · Claude reviewers on the same ten cases, for a quality-only comparison. Packets were anonymised and shuffled (`case-01`…`case-10`, key in `prototype/results/claude-blind-key.json`). Each reviewer saw only the requirements, the diff, and the `code_quality` rubric, never the user's labels or other results. A council of 3 on Sonnet (the runner's MVP setting) plus one Opus reviewer; scores in `prototype/results/claude.json`. Against the user's bands, with good ranked above flawed:
  - Claude Sonnet council (median of 3): same band 4/10, within one band 9/10, good above flawed 5/5. The three members were within 1 point of each other on every case.
  - Claude Opus: 4/10, 9/10, 5/5. Same band as the council on 8/10.
  - Jev 1.13: 3/10, 8/10, 4/5. Same band as the council on 4/10.
  - Laya multilingual (8192): 3/10, 7/10, 3/5. Same band as the council on 4/10. Laya typed-decisions (1024): 2/10, 5/10, 3/5.
  - Mean distance from the user's score: council 1.3 points, Opus 1.4. The largest gap was T04 good: the user gave 5 ("not tested"), every Claude reviewer gave 9, noting that the refactor keeps existing tests unchanged as required. Claude also scored the good variants higher than the user (T03 good 9 against 7) and T06 flawed less harshly (3 against 1). The council separated each pair by 5–7 points.
- 2026-10-19 · research (after closing, at the user's request: "I'm worried by the fact that the models seem to fail to identify bad code") · Second test, a question battery (`prototype/battery.py`, results in `prototype/results/battery.json`, report in `prototype/report2.html`; Claude not rerun). Same ten cases and state. Four generic questions per case, none naming a planted flaw, each mapped to 1–10, then the median: `criteria_met` (score, 5 levels), `tests_cover` (noul), `has_defect` (noul, inverted: contradicts the requirements for some input, a security flaw, or an unrequested behavior change), and `code_quality_band` (choice, band midpoints). Pass mark 6. Results as good above flawed / mean gap / flawed below 6 / good at 6 or above / mean distance from the user's score:
  - User: 5/5, 4.6, 5/5, 4/5, 0. Claude Sonnet council (first test): 5/5, 5.6, 5/5, 5/5, 1.3.
  - Jev median of 4: 5/5, 2.5, 2/5, 5/5, 2.1. Jev `has_defect` alone: 5/5, 3.9, 5/5, 5/5, 1.3. Jev's other questions are lenient (`criteria_met` caught 2/5, the band 2/5).
  - Laya multilingual median of 4: 3/5, 0.0, 2/5, 2/5, 2.7. Laya answered almost identically for both variants of every task (identical scores on T03, T04, and T11). Its `has_defect` said "defect" for every case (P about 0.85–0.95), good ones included, and `tests_cover` said "covered" for every case.
- 2026-10-19 · decision by the user · Given the trial, the MVP's shadow question uses both providers: Laya and Jev, through the same DecisionEngine adapter, both recorded next to the council and the human. Jev is limited to `sample-crm` (Q5).

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1

- **Decision (Q6):** Laya serving must be deployable in an environment without a Mac, such as a container on GKE. The owner's Mac may run it natively, on MPS, for local testing. `laya-serve` runs both ways (the README lists Docker), so one HTTP service with two ways to run it fits.
- **Fact (Laya README, model card, PyPI; probe):** Laya is a local typed-decision classifier. It takes a text, JSON, or turn-list state and typed questions (`choice`, `score`, `noul`). It returns a label, a probability distribution, `confidence` (1 minus normalised entropy), `answer_confidence`, and `usage` (tokens, and `truncated`). Version 0.4.12 is Apache-2.0 for code and weights, Python with PyTorch on CUDA, MPS, or CPU, and self-hosted only through `laya-serve`. Its state windows: `laya` about 430 tokens, `laya-typed-decisions` about 945, and `laya-multilingual` up to 8192. The shipped checkpoints are over-confident (ECE 0.213 on `laya-typed-decisions`) until temperatures are fitted.
- **Fact (TypeSafe docs; Jev trial):** Jev (`jev-1.13.0`) is TypeSafe's hosted, closed System One model, in early access, which the user has. Same question types and wire format (`POST https://api.typesafe.ai/v1/systemone`, bearer key). It returns a label, probabilities, and a `confidence` with no calibration claim. Its context is 64k per request; it costs $0.042 per million input tokens and takes about 300–350 ms on these packets.

### G2

- **Decision (Q7):** fine-tuning Laya on our own labelled code-review cases comes after the MVP, once real runs supply labels. It goes to BACKLOG "Next".
- **Decision (Q1):** the MVP bar for Laya is the plumbing plus an honest number. The contract and shadow logging work end to end, and agreement with the human and the council is recorded, whatever it is. Laya does not have to beat a baseline to stay in the MVP.
- **Fact (trials):** worth it in the MVP: typed step evaluation in shadow only (`code_quality_band`), for comparison data. Worth it next: human step assist with narrow risk checks, which is where a typed model already shows value (Jev's injection flag, 0.06 against 0.98). Transition guidance has only one branch point in the reference workflow, and run categorization needs real runs first. A zero-shot Laya adds no code-review signal in any role until it is fine-tuned.

### G3

- **Decision (Q8):** after the MVP's shadow question, later roles come in this order: (1) human step assist, as narrow `noul` risk flags shown at `release_approval`; (2) run categorization for the feedback loop; (3) transition guidance at `verification`, the only branch point today; and typed step evaluation last.
- **Fact (`sdlc-reference.yaml`, INTENT Modes, constitution art. 2):** typed step evaluation sits on the `code_quality` runner evaluator as a `typed_decisions` entry. It runs beside the council of 3, never earns points or blocks, and is recorded next to the council median and the human decision. Human step assist sits on `release_approval`, where the output may inform the approver but never decides; in advisory mode it is never shown to a fresh-context evaluator, to avoid anchoring. Transition guidance sits at `verification` (`approve_release`, `rework`, `discard`). Run categorization sits after a run ends, in the feedback loop.

### G4

- **Decision (Q9):** Claude drafts the ten variants (a good and a flawed one each for T01, T03, T04, T06, and T11), each in a session separate from any evaluation. The user reviews the code, then labels the cases without seeing any evaluator output.
- **Decision (Q10):** the user labels each case with a raw 1–10 score; the band follows from it (poor 1–3, weak 4–5, good 6–8, exemplary 9–10), so it compares directly with the council's median score.
- **Fact (Q11; `docs/test-bed.md`):** the `sample-crm` baseline exists (tag `baseline`, `0916dbf`, spec 004 done), so the variants branch from it, and the trial runs on real diffs.
- **Decision (Q2):** whole packets go to `laya-multilingual` with `max_len` up to 8192, so the full diff, tests included, fits instead of being cut. Its English is weaker and it has no fitted temperatures. **Fact (`prototype/tokens.py`):** all ten full packets fit in 8192 tokens on `laya-multilingual` (1,315–6,666), and none fits in the 1024-token checkpoints (the smallest is 1,172 on `laya-typed-decisions`). The briefing's estimates were too low: T04 is about 6,700 tokens, not 3,000.
- **Fact (probe, 2026-10-19):** `laya` 0.4.12 reports truncation in `usage.truncated` and `state_tokens_dropped`. So the adapter can return *not evaluated* whenever `usage.truncated` is true, without counting tokens itself. `laya-multilingual` at `max_len=8192` took a 7,224-token state in 1.1 s warm on MPS.
- **Fact (probe):** Java diff lines cost about 17 tokens each, so the 1024-token checkpoints hold only about 50 diff lines after the question head and the requirements.
- **Decision (Q3):** `code_quality_band` stays a 4-band `choice` question, with the step's band IDs as labels (schema rule 12), so it compares with the council and the human. One `noul` risk check is trialled on the T11 pair beside it.

### G5

- **Decision (user, after the trials):** the MVP's shadow question goes to both Laya and Jev, through one DecisionEngine adapter, and both results are recorded next to the council and the human. Laya stays the local baseline to fine-tune later; Jev gives a useful comparison now. Jev is used for `sample-crm` only (Q5).
- **Fact (battery, after closing):** asking a narrow defect question works much better than asking for a quality band. Jev's `has_defect` put all 5 flawed variants below the pass mark and kept all 5 good ones above it, as the Claude council did, with the same mean distance from the user (1.3). A median over several questions hides that signal, because the lenient questions pull it up. Zero-shot Laya separates nothing under any question. Shadow comparison in the MVP should record each question separately, not only a combined score.
- **Fact (Claude comparison, after closing):** on quality alone, the Claude council (Sonnet, median of 3) and a single Opus reviewer are the strongest evaluators here: within one band of the user on 9/10 and good above flawed on 5/5, against Jev 8/10 and 4/5, and zero-shot Laya 5–7/10 and 3/5. Exact band agreement is low for every evaluator (2–4/10), partly because the user grades good variants more strictly (T04 good: 5 against Claude's 9). So the typed models are weaker than the primary evaluator and suit shadow comparison, not replacement.
- **Fact (trials):** on these ten cases, Laya and Jev do not overlap in practice. Zero-shot Laya gives no usable code-review signal. Jev ranks good above flawed in 4–5 of 5 pairs, is stable under band reordering, sees whole diffs (64k context), and its narrow `noul` risk check is decisive (0.06 against 0.98 on T11). Its band is lenient: exact agreement is 3/10, but within one band 8/10.
- **Fact:** they complement each other on deployment. Laya is local, Apache-2.0, fast (75–530 ms on MPS), and can be fine-tuned, but needs our own labelled data to be useful on code. Jev is hosted, closed, and costs fractions of a cent per run, but sends code to a third party (acceptable for `sample-crm` only, Q5). Both speak `/v1/systemone`, so one DecisionEngine adapter fits both.
- **Decision (Q5):** `sample-crm` code and diffs may be sent to TypeSafe's hosted Jev API; any other repository needs a new decision.
- **Fact (user, Q4):** the user has Jev early access, so Laya and Jev can be compared on the same packets, not only on published figures.

### G6

- **Fact (Jev trial):** `jev-1.13.0` gave a real ordinal signal: good above flawed in 4–5 of 5 pairs, no position bias, and a decisive `noul` injection flag, at 300–350 ms per request. Exact band agreement is still at the trivial baseline (3/10) because it rates one band high.
- **Fact (trial):** no Laya checkpoint beats the trivial baseline on `code_quality_band` (at best 3/10 exact, against 3/10 for always answering a common band), none reliably ranks a good variant above its flawed twin (at best 3/5, against 2.5 by chance), and answers change with band order. This matches the model card: `laya-typed-decisions` was trained on non-code domains. With ten cases this is a comparison, not a calibration.
- **Fact (T03 ablation, after closing):** zero-shot `laya-multilingual` does not read the code against the requirements. With no diff it answers `good` (0.83), and with any long state it leans `poor`, lorem ipsum included (0.75). Its band says how much unfamiliar text the state holds, not how good the change is.
- **Fact (trial):** the narrow `noul` risk check did not separate the T11 pair either (0.835 vs 0.82), so narrowing the question does not rescue the zero-shot model.
- **Fact (trial):** latency is not a concern: p50 is 75–153 ms on the 1024 checkpoints and 300–530 ms on `laya-multilingual` with full packets up to 6,666 tokens, on the M5 Pro (MPS).
- **Fact (trial):** `laya-multilingual` at 8192 is the only checkpoint that saw the whole packet (0/10 truncated); the others were truncated on every case and would be *not evaluated* under the adapter rule.
- **Decision (Q2, Q12):** `laya-multilingual` at 8192 is in the trial, as the main candidate.
## Recommendation and decisions

<!-- Drafted when every goal question is answered, and agreed with the user. -->

| Decision | Choice | Alternatives considered | Why |
| --- | --- | --- | --- |
| What Laya must prove in the MVP | The plumbing works end to end in shadow mode, and agreement with the council and the human is recorded honestly, whatever it is (Q1) | Beat the most-common-label baseline to stay in the MVP | Shadow mode influences nothing, and influence is earned with evidence. The trial shows zero-shot Laya would fail a baseline bar. |
| MVP providers | Laya and Jev, both in shadow, through one DecisionEngine adapter speaking `/v1/systemone`; both recorded next to the council median and the human decision | Laya only (the original plan, Jev post-MVP); Jev only | The trial gave Jev a real ordinal signal (4–5 of 5 pairs, no position bias), and Laya none. The wire format is shared, so a second provider costs one config entry and about $0.003 per run. Laya stays the local, tunable baseline. |
| Jev data scope | `sample-crm` only; any other repository needs a new decision (Q5) | No third-party model APIs | `sample-crm` is public and owned by the user. Jev is hosted in the US and closed. |
| MVP typed question | `code_quality_band`: a 4-band `choice` with the `code_quality` rubric bands as labels, on `[requirements_doc, diff]` (spike 003, Q3) | `score` over the bands; replace it with `noul` risk checks | It stays comparable with the council and the human. `score` is the weakest type. Narrow risk checks come with human step assist after the MVP. |
| Laya checkpoint | `laya-multilingual` with `max_len` 8192 (Q2) | `laya-typed-decisions` (1024); base `laya` (512); compacted diffs; `predict_long` windows | It is the only checkpoint that saw every full packet (1,315–6,666 tokens). The others were truncated on 10 of 10 cases. None beat chance, so the choice rests on seeing the whole input. |
| Over-long input | The adapter records *not evaluated* whenever the provider reports truncation (`usage.truncated` in `laya` 0.4.12) | Send it anyway; count tokens in the engine | Fail closed (art. 5): an answer about half a diff must not be recorded as if it saw the whole change. The library reports truncation itself. |
| Laya runtime and serving | `laya-serve` (Python, FastAPI, `/v1/systemone`) as a container image that runs without a Mac (CPU or GPU, e.g. on GKE); natively on the owner's Mac (MPS) for local testing (Q6) | `laya-java` ONNX inside the engine (pre-release, no export for these checkpoints); CPU only in the cluster | It is the most mature path, keeps the Apple GPU for testing, and stays deployable anywhere. |
| Hardware | The M5 Pro for the MVP: p50 75–530 ms per question on MPS, with full packets up to 6,666 tokens | — | Latency is far below what a checkpoint that waits on a runner and a human needs. |
| Labelling | The user labels each case with a raw 1–10 score, blind to every evaluator; the band is derived (Q10). Claude drafts the variants in a separate session (Q9). | Band-only labels; the user writes the variants | A score compares with the council median, and drafting stays separate from judging. |
| Fine-tuning | After the MVP, once real runs supply labelled cases (Q7) | Never; now | It is the only route to a useful Laya code-review signal; ten cases are far too few. |
| Order of later roles | (1) Human step assist with narrow `noul` risk flags at `release_approval`; (2) run categorization; (3) transition guidance at `verification`; typed step evaluation last (Q8) | Categorization first; guidance first | Jev's injection flag (0.06 against 0.98 on T11) shows narrow risk checks are where a typed model helps a human first. |

## Deliverables

- [x] Every goal question answered, and the recommendation agreed with the user
- [x] BACKLOG.md items settled by this spike updated

## Follow-ups

<!-- New specs, BACKLOG items, experiments, or open questions this spike created. -->

Where each follow-up went (agreed with the user on 2026-10-19 as part of the recommendation):

- **BACKLOG "Next" (user, after closing):** the actual questions asked of Jev, including whether to add a `has_defect` check beside `code_quality_band`, are reviewed later, using the battery in `prototype/report2.html` as evidence. The spike stays closed with the agreed recommendation.
- **New spike (user, after closing):** Jev telemetry, spec 008.

- **Done:** `docs/definitions/schema.md` (rule 12 and the typed-decisions example) and `sdlc-reference.yaml` now allow provider `jev` beside `laya`, with a second shadow entry for `code_quality_band`. The `max_len: 8192` checkpoint setting belongs to the adapter's configuration, not the definition.
- **Done:** `WORKFLOW_ENGINE_INTENT.md` (Components, Hosting, Phases 5 and 9) and `PLAN.md` (Phase 1 Laya item and exit criteria) now have Jev as a shadow provider in the MVP.
- **BACKLOG "Next":** "Laya fine-tuning" (including fitted temperatures) and "Human step assist with risk flags" (starting from the T11 injection check).
- **BACKLOG spikes 7, 9, 10:** inputs added for the multi-provider adapter, the truncation rule, the Jev key and egress, the `laya-serve` image, and running it natively or in the cluster.
- **Later, no new item:** the ten cases and labels join the held-out set when real runs of the same tasks exist, as `docs/test-bed.md` ("Laya's first cases") already plans.
- **Checked, no change:** the constitution. Jev is a DecisionEngine provider called only by the engine (art. 4), and art. 2 bars it from deciding human steps just like Laya.

Things to try, from the briefing (all done on 2026-10-19 except where noted):

- Token count (about 1 h): write the T01 and T04 good variants in a scratch Spring Boot project. Count the requirements, the diff (full, then compacted), and the question head with the `laya-typed-decisions` tokenizer.
- Truncation check (about 30 min): send an over-long packet through `laya-serve`, and confirm that the end is dropped without a warning.
- Checkpoint trial (about half a day, once the variants exist): run the ten cases on each checkpoint and in two band orders. Record the label, distribution, `answer_confidence`, and agreement with the blind labels, the Claude council, and the most-common-label baseline. Measure cold and warm latency on the M5 Pro.
- A `noul` risk check on the T11 pair (about 1 h).
- A Jev comparison on the same packets, if there is access (about 1 h).

## Changelog

- 2026-10-19 Created.
- 2026-10-19 Briefing and questions prepared; in progress.
- 2026-10-19 All questions answered; Laya and Jev trials run; recommendation drafted.
- 2026-10-19 Recommendation agreed with the user.
- 2026-10-19 Verified and closed: BACKLOG Laya items and spike 6 settled; PLAN spike 6 ticked; definition docs and intent updated for Jev in shadow.
- 2026-10-19 After closing: the second test (question battery) and the Claude comparison added as evidence; the review of Jev's questions goes to BACKLOG "Next", and Jev telemetry gets its own spike.
