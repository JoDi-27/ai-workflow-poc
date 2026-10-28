---
id: 011
title: "Driver API contract"
type: spike
status: done
phase: 0
depends_on: [003, 005, 010]
refs: ["PLAN Phase 0 / Spikes / 8. Driver API contract", "BACKLOG Spikes / Wave 3 / 8. Driver API contract", "BACKLOG MVP / MCP server", "BACKLOG MVP / Hooks to engine", "BACKLOG MVP / Result delivery to the driver"]
updated: 2026-10-21
---

# 011 · Driver API contract

<!--
A spike answers questions and settles decisions; it does not ship production code. Run it with /sdd-spike 011:
the spike-researcher agent prepares a briefing and questions, you answer and add information over as many sessions
as it takes, and everything learned is recorded here. Prototypes are throwaway and live under specs/011-*/prototype/.
Progress counts the checkboxes in Goal questions, Questions for you, and Deliverables.
-->

## Inbox

<!-- Drop notes, links, constraints, or decisions here at any time, in any form. The next /sdd-spike session moves them into the Log and Findings and clears this section. -->

## Why

<!-- The decision this spike unblocks, and the BACKLOG.md MVP items it settles. -->

Spike 010 settled the system's shape: components, flows, persistence, and trust boundaries, with the architecture in `docs/architecture/`. Spike 005 proved that Claude Code can drive the engine through a remote MCP server and command hooks. Spike 003 drafted the definition model, including the subject key derived from `run_inputs` and the evaluation task packet built from a step's instructions, rubric, and inputs.

What is still missing is the exact boundary every driver kit is written against: the MCP operations and their schemas, the HTTP endpoints hooks call, denial messages, idempotency keys, the evaluation task packet, and the capabilities a session declares. Without it, the Claude Code kit would end up defining the API, and the engine would stop being provider-neutral.

This spike defines that contract. It unblocks spike 9 (the MCP and HTTP layer of the technology stack) and the driver API scaffolding in Phase 0.

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [x] **G1** The input and output schemas of each MCP operation, and which ones the MVP needs.
- [x] **G2** The HTTP equivalents that hooks call, and how they are authenticated.
- [x] **G3** The format of denial messages, idempotency keys, and the evaluation task packet (step, rendered rubric, evidence references).
- [x] **G4** Which driver capabilities a session declares, and how the engine checks them.
- [x] **G5** What `status` carries so that a generic driver can choose its next action from it alone (article 9): the stage task, permitted transitions and their checkpoints, pending evaluation tasks, pending human steps, recommendations, and the last decision with its reason; and how iterations and exhaustion escalations are reported.
- [x] **G6** Who computes the subject key and how it is derived from the definition's `run_inputs`, and whether a runner evaluator reads the driver's `diff` or diffs its own clone.

## Briefing

<!-- Prepared by the spike-researcher agent: what the repository and current docs already say, with sources. -->

Prepared 2026-10-21 by spike-researcher. INTENT = `docs/intent.md`, ARCH = `docs/architecture/README.md`, SEQ = `docs/architecture/sequences.md`.

### G1 · MCP operations and schemas

- Known: the engine's HTTP driver API is the only driver contract. A stateless MCP gateway mirrors it and forwards the caller's driver token unchanged. (spec 010 Q20; ARCH Components)
- Known: MCP tools take the subject as an argument, because MCP calls do not carry Claude Code's session ID. Claude Code names plugin tools `mcp__plugin_<plugin>_<server>__<tool>`. (spec 005 G2, trial 2)
- Known: the `DriverOperations` use cases are register/bind session, `start_run`, `status`, `submit_evidence`, `request_transition`, `checkpoint_status`, `get_evaluation_task`, `submit_step_result`, `record_activity`, `authorize_action`, and `submit_draft`. (ARCH Inbound ports) INTENT (Driver integration) also lists `bind_session` and `allowed_actions`.
- Known: the reference workflow submits no evidence, and a transition request carries only the revision (spec 010 Q26). The driver token may read analysis and submit drafts, never publish (Q15). Analysis reads go to the analysis API (Q23).
- Gaps: no operation reports a stage-task iteration, although SEQ 6 has "report iteration". No operation reads the current definition YAML, which `submit_draft` needs to diff against.
- Suggested draft operation list (HTTP under `/v1`; MCP tools mirror the calls marked MCP):

  | Operation | Transport | MVP? | Input → output (draft) |
  | --- | --- | --- | --- |
| Contract boundary | The engine's HTTP API under `/v1` is the contract; MCP tools mirror part of it through the gateway; hooks call HTTP directly | An MCP-only contract; separate hook and MCP contracts | Spec 010 Q20; hooks cannot speak MCP, and one contract keeps kits thin |
| Subject key (Q1) | The definition marks the key inputs (`subject.key`), the engine derives a canonical key | Kit computes an opaque key; key template | A kit cannot send a key that disagrees with what the runner clones; the engine joins opaque strings only |
| MCP idempotency (Q2) | Gateway hashes (user, tool, arguments) into `Idempotency-Key`; mutating tools echo `expected_attempts` | Model-supplied `request_id`; natural keys only | Blind retries are safe, deliberate retries get a new key, and the agent never manages keys |
| Session on MCP calls (Q3) | PreToolUse injects `session_id` into engine tool arguments with `updatedInput` | Bind all same-user sessions on the subject; explicit bind hook | Exact binding and capability check per session. To confirm by trial before the kit is built |
| Stage-task limits (Q4, Q5) | The engine counts iterations and returns `continue` or `stop`; a failed check is a self-check with unmet criteria | Kit enforces limits; Stop without a request, or a failed attempt, as a failed check | One count, proven in spike 005; the glossary already calls the criteria the driver's own completion check |
| Failure detail (Q6) | Status gives outcome, a one-line reason, a trimmed log tail, and council bands and rationales; no raw scores | Reason only; reason and log tail | What a human reviewer would give; spike 005 G9 showed a generic driver cannot rework without it |
| No run yet (Q7) | Status lists startable workflows and their input descriptions | Kit default workflow; user always names it | Keeps the kit workflow-free (Art. 9) |
| MVP scope (Q8) | Build status, start_run, request_transition, get_definition, submit_draft, get_analysis, and the four hook endpoints; specify submit_evidence, get_evaluation_task, submit_step_result | Also build submit_evidence; drop the feedback-loop tools | Happy path first (Art. 10); the reference workflow uses none of the spec-only ones |
| Capabilities (Q9) | Declared at register, trusted and recorded, checked at bind | Flag unobserved capabilities; refuse on mismatch | Art. 8: record what cannot be enforced; observed checks go to "Next" |
| Rubrics in status (Q10) | Full rubric, `min_score`, and points shown to the driver; still left out of the evaluator packet | Names and roles only; nothing | `get_definition` exposes them anyway; the evaluator stays unanchored |
| SessionStart context (Q11) | Register returns a compact `summary`; full detail through the `status` tool | Full status, trimmed or not | Claude Code's 10,000-character cap on injected context |
| Denials | `{code, message, recovery, details}`; problem+json on MCP-backed calls; the gate always 200 | Plain error strings; HTTP errors on the gate | Stable codes for kits, readable text for agents; a hook script's error handling cannot swallow the reason |
| Neutral hook responses | The engine answers in neutral JSON; the kit maps it to Claude Code hook output | Engine returns Claude Code hook JSON (spike 005 stub) | Art. 3; the stub's shape is not carried over |
  | `status` | MCP + `GET /v1/runs/{id}/status`, `GET /v1/sessions/{sid}/status` | MVP | subject inputs or run ID → status (G5) |
  | `start_run` | MCP + `POST /v1/runs` | MVP | `workflow`, `run_inputs` → status, or a denial (`subject_busy`, `missing_capability`, `no_registered_session`) |
  | `request_transition` | MCP + `POST /v1/runs/{id}/transition-requests` | MVP | `transition`, `revision` (+ precondition, Q2) → accepted, attempt open (attempt ID), or denial, plus status |
  | `checkpoint_status` | folded into `status` (`open_attempt`) | folded | — |
  | `allowed_actions` | folded into `status` (`permitted_transitions`, `protected_actions`) | folded | — |
  | `get_definition` | MCP + `GET /v1/workflows/{wf}/versions/{v\|current}` | MVP (new) | → YAML, version |
  | `submit_draft` | MCP + `POST /v1/workflows/{wf}/drafts` | MVP | `yaml`, `base_version`, `hypothesis` → draft ID, or validation errors |
  | `get_analysis` | MCP → analysis API | MVP | → weakest step, rates, agreement |
  | `submit_evidence` | HTTP + MCP | schema only | `type`, `payload`, `revision` |
  | `get_evaluation_task`, `submit_step_result` | MCP | later (driver-side evaluation) | task ID → packet; task ID + raw result |

### G2 · HTTP endpoints for hooks, and auth

- Known: hooks are command hooks only, calling the HTTP API through one script. SessionStart cannot be an `http` hook, and `http` hooks cannot read the plugin's token. (spec 005 Recommendation)
- Known: PreToolUse runs on every tool with `onFailure: "block"` and a 10 s timeout, so an engine outage blocks every tool call (spec 005 Q6). PostToolUse activity is async. The Stop hook drives stage tasks.
- Known: auth is `Authorization: Bearer <driver token>`, one static token per user, stored hashed. The same token works on hooks, the gateway, and OTLP. A wrong token gets 401. (spec 010 Q4, Q14; spec 005 trial 1)
- Known: unknown sessions register lazily on their first PreToolUse. Forks and `/clear` register again. (spec 005 G5; spec 006)
- Known (Art. 3; spec 010 Q3): the engine answers hooks in neutral JSON, and the kit script maps it to Claude Code's `hookSpecificOutput`. The spec 005 stub returned Claude Code hook JSON from the engine (`prototype/stub-engine/.../Main.java`); that is a neutrality leak and does not carry over.
- Suggested hook endpoints, all MVP:
  - `POST /v1/sessions` (register): `session_id`, `source`, subject inputs, `driver{provider, agent_version, model, kit_version, contract_version, capabilities}` → session, status or null, and a compact `summary`.
  - `POST /v1/sessions/{sid}/gate` (`authorize_action`, action optional): `tool_use_id`, `action?{name, revision}` → `decision` (allow or deny), `code`, `reason`, `recovery`. Doubles as the liveness check and the lazy registration trigger.
  - `POST /v1/sessions/{sid}/activity` (async): `tool_use_id`, tool, input, outcome, `duration_ms`, `agent{id, type}` → 202.
  - `POST /v1/sessions/{sid}/iterations` (Stop): the completion-check result (Q5) → `continue` or `stop`, with a reason and the counts (Q4).
- Known, also part of the contract (spec 010 Q23): the introspection endpoint (token or session → user, kind, scopes), the paged event read (`after` cursor), and the analysis read API.

### G3 · Denials, idempotency keys, evaluation task packet

- Known: every mutating call and every human decision takes an idempotency key, following IETF Idempotency-Key draft-07: the same key with a different payload is rejected, and a retry returns the stored result. (spec 010 Q16)
- Known: MCP has no request-level idempotency; `idempotentHint` is an untrusted hint (MCP schema 2025-11-25). Where an MCP call's key comes from is open (Q2).
- Known: denials must be useful (INTENT Driver integration). In spec 005 G9, a generic driver could not rework without the last decision's reason.
- Known denial categories: not permitted now; stale revision; attempt already open (model.md, transition request lifecycle); lease held by another user (spec 010 Q14); missing capability (INTENT Driver contract); stage not in `allowed_in`, or revision ≠ reviewed revision (schema.md, Authorization rules).
- Suggested denial shape: `{code, message, recovery, details?}`, with `code` from a stable enum, `message` one plain sentence, and `recovery` the generic next step. Over HTTP: RFC 9457 problem+json for MCP-backed calls, and a 200 `deny` for the gate (curl `--fail` would otherwise replace the reason with a generic failure). The gateway maps a denial to an `isError: true` result; the kit maps a gate deny to `permissionDecisionReason`.
- Suggested hook keys: gate and activity use Claude Code's `tool_use_id`; the script generates one key per call for register and iterations (it does not retry).
- Known, runner-side evaluation task packet (MVP): task ID, step, rendered instructions and rubric, scale, the subject inputs it resolves from its own clone (`requirements` file, `diff` from base), repository, base, and revision, bounded by the runner default timeout. (ARCH Runner interface; spec 010 Q6, Q26) The output is a draft-07 schema (raw score and rationale); evaluators report raw results only and the engine applies `min_score` (Art. 6; spec 005 G4).
- Suggested: the packet leaves out `min_score` and points, so as not to anchor the evaluator. The driver-side packet (`get_evaluation_task`, post-MVP) is the same packet.

### G4 · Capabilities

- Known: capabilities are `core`, `activity_recording`, `evaluation`, `enforcement`, and `telemetry`. A definition requires some of them; the reference workflow requires `core`, `activity_recording`, and `enforcement`. (schema.md; `sdlc-reference.yaml`)
- Known: the engine refuses to bind a session that lacks a required capability. A session records its provider, agent version, model, kit version, and capabilities. (INTENT Driver contract; glossary Driver session)
- Known: the laptop is trust-based, so the engine records what it cannot enforce (Art. 8).
- Suggested: capabilities are declared in the register payload and checked when `start_run` binds, or when a session binds to an existing run.
- Open: whether declared capabilities are only trusted or also checked against observed calls (Q9); how an MCP-only `start_run` relates to a registered session (Q3).

### G5 · What `status` carries

- Known: status carries the stage task, the permitted transitions with their checkpoints, pending evaluation tasks, pending human steps, recommendations, and the last decision with its reason. (BACKLOG spike 8 inputs; INTENT Transition protocol step 1; spec 005 G9)
- Known: at a human gate the session ends, and a later session resumes from the status injected at SessionStart (spec 010 Q7). For an exhaustion escalation resolved with "rework", the engine commits the rework transition, and status shows it (Q8).
- Known: shadow typed decisions influence nothing, so `recommendations` stays empty in the MVP and tree conclusions never reach the driver. (INTENT Modes; spec 010 Q28)
- Known: the kit enforces stage-task limits and the engine records each iteration (spec 001 Q23). The engine measures wall time itself (model.md). In the spec 005 prototype, the engine counted Stop firings and returned the block or stop verdict (trials 18–19).
- Suggested status shape:
  - `run{id, workflow, version, state, subject_key, run_inputs, owner, lease_holder}`
  - `stage{id, visit, entered_at, task{prompt, acceptance_criteria (rendered), limits, used{iterations, wall_time, consecutive_failed_checks}, state: working|exhausted}}`
  - `permitted_transitions[{id, to, rework, checkpoint{label, steps[{id, executor, role, runs_in}]}}]`
  - `protected_actions[{name, allowed_now, reviewed_revision}]`
  - `open_attempt{id, transition, revision, steps[{id, state}], waiting_on: runner|human}`
  - `last_decision{kind, at, actor_type, reason, failed_steps[{id, outcome, reason}], rework_path}`
  - `pending{human_steps, escalations, evaluation_tasks: []}`
  - `recommendations: []`
  - `next{action: do_stage_task|request_transition|wait|none, hint}`, generic only
- Open: iteration semantics (Q4, Q5), how much failure detail the driver sees (Q6), and whether rubrics are shown (Q10).

### G6 · Subject key; evaluator diff

- Known: the runner evaluator diffs its own clone; `diff` is dropped as evidence. (spec 010 Q26) This half of G6 is settled.
- Known: the engine knows only an opaque subject key, plus `run_inputs` stored "without interpretation". At most one active run per workflow and subject key, enforced by a unique constraint. For SDLC the key is repository + branch. (spec 001 Q1; schema.md; spec 010 Q14)
- Conflict on who computes the key: `docs/concepts/glossary.md` (Subject) and spec 001 say the driver kit (marked an assumption there); SEQ 1 has the engine "derive subject key"; spec 003 handed "whether the definition says which inputs form the key" to this spike. (Q1)
- Known: the SessionStart hook sees only `session_id` and `cwd`, so the kit computes repository + branch locally either way (spec 005 G2).

### Researched

- MCP 2026-07-28 tools: `outputSchema` is optional, JSON Schema 2020-12 by default. If present, the server MUST return conforming `structuredContent`, and SHOULD also return the serialized JSON as a text block. Business and validation errors go in the result with `isError: true`, and clients SHOULD pass them to the model; unknown tools and malformed requests are JSON-RPC errors. There are no protocol sessions, so state lives in explicit handles passed as arguments, authorized on every call. Tool names are 1–128 characters from `[A-Za-z0-9_.-]`; the tool list MAY vary by the caller's authorization. ([tools 2026-07-28](https://modelcontextprotocol.io/specification/2026-07-28/server/tools), checked 2026-10-21)
- MCP 2025-11-25 schema: `idempotentHint` defaults to false and `destructiveHint` to true; all annotations are untrusted hints; `outputSchema` must have an object root. ([schema 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25/schema))
- Claude Code, community reports only (unconfirmed): when `structuredContent` is present, the model gets its JSON and not the text block; the model never sees `outputSchema`; one open issue reports `outputSchema` validation running on the whole result envelope. ([79944](https://claudeissues.com/issue/79944-mcp-tool-response-text-content-block-dropped-when-structuredcontent-is-also-pres), [54197](https://claudeissues.com/issue/54197-feature-surface-mcp-tool-outputschema-to-the-model-context-not-just-the-renderer), [76257](https://claudeissues.com/issue/76257-mcp-structuredcontent-validation-appears-to-check-the-whole-calltoolresult-envel), [client survey](https://olaservo-content-vs-structuredcontent.static.hf.space/client-survey.html))
- Claude Code hooks ([docs](https://code.claude.com/docs/en/hooks), checked 2026-10-21):
  - PreToolUse `permissionDecision` is `allow`, `deny`, `ask`, or `defer`. On `deny`, `permissionDecisionReason` is shown to Claude; on `ask`, to the user; on `allow` or `defer`, only to the debug log. Exit 2 blocks with stderr as the message. `updatedInput` replaces the tool's arguments.
  - Stop: `decision: "block"` needs a `reason` that tells Claude why to continue; `continue: false` with `stopReason` ends processing. The input carries `stop_hook_active`, `last_assistant_message`, `background_tasks`, and `session_crons`; `background_tasks` may let the kit skip counting a stop while a subagent still runs.
  - `additionalContext`, `systemMessage`, and stdout are each capped at 10,000 characters. Above it Claude gets a file path and a 2,000-character preview, and is not asked to read the file. So the status injected at SessionStart plus the kit instructions must stay under 10k characters.
  - `onFailure: "block"` (Claude Code ≥ 2.1.295) makes a failure act like exit 2; it has no effect on Stop or async hooks.

## Questions for you

<!-- Prepared by the agent, highest impact first; each says which goal questions it serves, why it matters, the options, and a suggested answer. Ticked when answered or skipped. -->

- [x] **Q1** Who derives the subject key? (→ G6, G1, G5) It fixes the `start_run` and `status` inputs and how the unique constraint works across kits and users, and resolves the glossary vs SEQ 1 conflict. *Options:* the definition marks which `run_inputs` form the key (for example `subject_key: [repository, branch]`) and the engine builds a canonical key / the kit computes an opaque key and sends it with `run_inputs` / a key template in the `subject` block. *Suggested:* the definition marks the inputs and the engine derives the key, so a kit cannot send a key that disagrees with what the runner clones, and the engine only joins opaque strings.
- [x] **Q2** Where does the idempotency key come from for MCP calls? (→ G3, G1) A lost response and a retried `request_transition` could open a second failing attempt (a 5-point deduction). *Options:* the gateway hashes (tool, arguments) into the key, and mutating tools echo a precondition from status (for example the last attempt number) / the model passes a `request_id` / natural keys only (the open attempt absorbs duplicates). *Suggested:* gateway hash plus echoed precondition.
- [x] **Q3** How does an MCP `start_run` bind the calling session, given MCP calls carry no session ID? (→ G4, G1, G2) *Options:* bind every registered session of the same user on the same subject (as the spike 005 stub did) / the PreToolUse hook injects `session_id` into the tool's arguments with `updatedInput` (untested) / a hook calls an explicit bind after `start_run`. *Suggested:* same user plus same subject for the MVP; refuse `start_run` when no registered session with the required capabilities exists.
- [x] **Q4** On each Stop, does the engine return the continue-or-stop verdict, or does the kit decide from the limits in status and only report? (→ G5, G2) Spec 001 Q23 says the kit enforces limits; the spike 005 prototype had the engine decide. *Suggested:* the engine returns the verdict: one count, proven in trials 18–19, a thin kit.
- [x] **Q5** What counts as one failed completion check for the `failed_checks` limit? (→ G5) *Options:* the agent's self-check against the acceptance criteria, reported with each iteration / a Stop with no transition request since the last iteration / a failed checkpoint attempt. *Suggested:* the self-check reported on the iteration, naming the unmet criteria.
- [x] **Q6** After a failed attempt, how much detail does status give the driver? (→ G5, G3) *Options:* step, outcome, and a one-line reason / plus the log tail of a failed `command` check / plus each council member's band and rationale. *Suggested:* all three, log tail trimmed to a few KB; raw scores stay engine-side.
- [x] **Q7** How does a generic driver choose the workflow and fill `run_inputs` when no run exists? (→ G1, G5) *Options:* status with no run lists the workflows the user may start, with their input descriptions / the kit config names a default workflow / the user always names it. *Suggested:* status lists the startable workflows.
- [x] **Q8** Which operations does the MVP build, and which are only specified? (→ G1) *Suggested:* build `status`, `start_run`, `request_transition`, `get_definition`, `submit_draft`, `get_analysis`, and the four hook endpoints; fold `checkpoint_status` and `allowed_actions` into `status`, and `bind_session` into register and `start_run`; specify only `submit_evidence`, `get_evaluation_task`, and `submit_step_result`.
- [x] **Q9** Should the engine only record declared capabilities, or also flag a session whose behaviour does not match them? (→ G4) *Options:* declared and recorded only / also flag "declared but never observed" (for example no gate calls) / refuse later calls. *Suggested:* declared and recorded, refusing binding when one is missing; the observed check goes to "Next".
- [x] **Q10** Does status show the checkpoint's rubrics, `min_score`, and points before the driver requests the transition? (→ G5, G3) *Options:* full rubric and thresholds / step names and roles only / nothing. *Suggested:* the full rubric, since `get_definition` already exposes it.
- [x] **Q11** Full rubrics (Q10) and council rationales (Q6) make status large, but the status injected at SessionStart, together with the kit instructions, must stay under Claude Code's 10,000-character cap or the agent only sees a 2,000-character preview. What does SessionStart inject? (→ G5, G2) *Options:* a compact summary (stage, task, permitted transitions, open attempt, last decision's one-line reasons, and `next`), with the agent calling the `status` tool for full detail / the full status, trimmed to fit / the full status only, accepting the cap. *Suggested:* the compact summary; the register endpoint already returns a `summary` for this.

## Log

<!-- Append-only, oldest first: "- YYYY-MM-DD · <source> · <what was answered, added, or found>". -->

- 2026-10-21 · research · Briefing prepared. G6 half settled by spec 010 Q26 (the runner evaluator diffs its own clone). Found a conflict on who computes the subject key (glossary/spec 001 vs SEQ 1), raised as Q1. Found the spec 005 stub returned Claude Code hook JSON from the engine, a neutrality leak not to carry over.
- 2026-10-21 · answer to Q1 · The definition marks which `run_inputs` form the subject key (for example `subject_key: [repository, branch]`), and the engine derives a canonical key from them. Supersedes the glossary and spec 001 assumption that the kit computes it.
- 2026-10-21 · answer to Q2 · For MCP calls, the gateway hashes (tool, arguments) into the idempotency key, and mutating tools echo a precondition from status (for example the last attempt number).
- 2026-10-21 · answer to Q3 · An MCP `start_run` binds the calling session through the PreToolUse hook injecting `session_id` into the engine tool's arguments with `updatedInput`. Untested: confirm with the `updatedInput` trial in Follow-ups.
- 2026-10-21 · answer to Q4 · On each Stop, the kit reports the iteration and the engine returns the continue-or-stop verdict with a reason and the counts. Supersedes spec 001 Q23, where the kit enforced the limits.
- 2026-10-21 · answer to Q5 · One failed completion check is the agent's self-check against the stage task's acceptance criteria, reported with each iteration, naming the unmet criteria.
- 2026-10-21 · answer to Q6 · After a failed attempt, status gives each failed step's outcome and a one-line reason, the trimmed log tail of a failed `command` check (a few KB), and each council member's band and rationale. Raw scores stay engine-side.
- 2026-10-21 · answer to Q7 · With no run, status lists the workflows the user may start, with their input descriptions; the agent fills `run_inputs` from the user's request and git.
- 2026-10-21 · answer to Q10 · Status shows the checkpoint's full rubrics, `min_score`, and points before the transition request.
- 2026-10-21 · answer to Q8 · The MVP builds `status`, `start_run`, `request_transition`, `get_definition`, `submit_draft`, `get_analysis`, and the four hook endpoints (register, gate, activity, iterations). `checkpoint_status` and `allowed_actions` fold into `status`; `bind_session` folds into register and `start_run`. `submit_evidence`, `get_evaluation_task`, and `submit_step_result` are specified only.
- 2026-10-21 · answer to Q9 · The engine trusts and records declared capabilities, and refuses binding when a required one is missing. Checking observed behaviour against declarations goes to BACKLOG "Next".
- 2026-10-21 · answer to Q11 · SessionStart injects a compact summary (stage, task, permitted transitions, open attempt, the last decision's one-line reasons, and `next`); the agent calls the `status` tool for full detail.
- 2026-10-21 · note from the user · Agreed the recommendation, including the drafted contract boundary, denial format, neutral hook responses, and evaluation task packet (by asking to run verify).

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1

- **Decision** (Q3): MCP tools that need the calling session (at least `start_run`) take a `session_id` argument that the kit's PreToolUse hook fills in with `updatedInput`; the agent never supplies it. To confirm by trial.
- **Decision** (Q2): mutating MCP tools carry a precondition echoed from status (for example `expected_attempt`).
- **Decision** (Q7): `status` also works with no run; it then lists the startable workflows and their `run_inputs` descriptions, so `start_run` needs no workflow knowledge in the kit.
- **Decision** (Q8): MVP builds `status`, `start_run`, `request_transition`, `get_definition`, `submit_draft`, `get_analysis` as MCP tools over HTTP; `checkpoint_status` and `allowed_actions` fold into `status`, `bind_session` into register and `start_run`; `submit_evidence`, `get_evaluation_task`, `submit_step_result` are specified only. Schemas in `docs/driver-api/`.

### G2

- **Decision** (Q4): the Stop hook calls an iterations endpoint; the engine counts and answers `continue` or `stop` with a reason and the counts. The kit holds no limit logic.
- **Decision** (Q3): the PreToolUse hook also rewrites engine MCP tool calls to add `session_id` (`updatedInput`).
- **Decision** (Q5): the iterations request carries the self-check result: `criteria_met` (bool) and the unmet criteria. A self-check with unmet criteria counts as one failed check.
- **Decision** (Q8): the four hook endpoints (register, gate, activity, iterations) are all MVP. Auth is the driver bearer token (spec 010 Q4, Q14).
- **Decision** (Q11): the register response carries a compact `summary` that SessionStart injects; the full status is fetched with the `status` tool.

### G3

- **Decision** (Q2): idempotency keys for MCP calls are the gateway's hash of (tool, arguments); the echoed precondition makes a deliberate retry a different payload, so it gets a new key. Hook calls use their own keys (see Briefing G3).
- **Decision** (Q10): the driver may see rubrics and thresholds, but the runner's evaluator packet still leaves out `min_score` and points (Briefing G3), since that packet goes to a fresh-context evaluator, not to the driver.
- **Decision** (agreed with the recommendation): denials are `{code, message, recovery, details}` from a stable code enum; MCP-backed calls return them as RFC 9457 problem+json with 403, 404, 409, or 422, and the gate always answers 200 with `decision: deny`. The gateway turns a denial into an `isError: true` result; the kit turns a gate deny into `permissionDecisionReason`. Code list in `docs/driver-api/README.md`.
- **Decision** (agreed with the recommendation): the evaluation task packet is the same for the runner and a later driver-side evaluator, and leaves out `min_score` and points. Shape in `docs/driver-api/README.md`.

### G4

- **Decision** (Q3): binding uses the `session_id` the hook injects, so the engine checks the capabilities of that exact registered session when `start_run` binds it.
- **Decision** (Q9): capabilities are declared at register, trusted and recorded on the session, and checked when a session binds; a missing required capability refuses the bind (`missing_capability`). Observed-behaviour checks go to BACKLOG "Next".

### G5

- **Decision** (Q4): the engine owns the iteration count and the verdict, so status shows `used` and `limits` as reported facts, not as inputs for the kit to enforce.
- **Decision** (Q5): `used.consecutive_failed_checks` counts self-checks with unmet criteria.
- **Decision** (Q6): `last_decision.failed_steps[]` carries outcome, a one-line reason, a trimmed `log_tail` for failed `command` checks, and per-member `band` and `rationale` for councils. No raw scores.
- **Decision** (Q7): with no run, status carries `startable_workflows[{id, version, description, run_inputs[{name, description, required}]}]`.
- **Decision** (Q10): `permitted_transitions[].checkpoint.steps[]` carry the rendered rubric, `min_score`, and points.
- **Decision** (Q11): status has two renderings: the full object from the `status` tool, and a compact `summary` (stage, task, permitted transitions, open attempt, last decision's one-line reasons, `next`) for SessionStart, kept with the kit instructions under 10,000 characters.

### G6

- **Fact** (spec 010 Q26): the runner evaluator diffs its own clone; `diff` is not driver evidence.
- **Decision** (Q1): the definition marks which `run_inputs` form the subject key, and the engine derives a canonical key from them. The kit only sends `run_inputs` (it computes repository and branch locally from `cwd`). The glossary and SEQ 1 need updating to match.

## Recommendation and decisions

<!-- Drafted when every goal question is answered, and agreed with the user. -->

| Decision | Choice | Alternatives considered | Why |
| --- | --- | --- | --- |

## Deliverables

- [x] Every goal question answered, and the recommendation agreed with the user
- [x] The operation list with draft schemas, and one happy path run written as a sequence of calls, in `docs/driver-api/`
- [x] BACKLOG.md items settled by this spike updated

## Follow-ups

<!-- New specs, BACKLOG items, experiments, or open questions this spike created. -->

- Things to try (from the briefing), checked when the kit is built (`structuredContent` visibility, Stop `background_tasks`, summary size; the call sequence is done in `docs/driver-api/happy-path.md`):
  - MCP result visibility in current Claude Code: one stub tool returning `structuredContent` with `outputSchema` plus text, one returning text only; record what the model sees and whether the `outputSchema` validation bug appears. Reuses the spike 005 stub, about 1 h.
  - **Needed by the Q3 decision:** PreToolUse `updatedInput` on an engine MCP tool to inject `session_id`, about 1 h.
  - Stop hook `background_tasks` while a subagent runs, to fix spike 5's early-stop counting, about 1 h.
  - Render the reference workflow's status at the open `approve_release` attempt with the kit instructions and measure it against the 10,000-character cap, on paper, about 30 min.
  - Write T01's happy path as a call sequence (the `docs/driver-api/` deliverable) once Q1–Q4 are answered, about 2 h.
- *Done in verify:* update `docs/concepts/glossary.md` (Subject) and SEQ 1 wording: the engine derives the subject key from the inputs the definition marks (Q1). Update the stage-task limits wording from spec 001 Q23: the engine returns the verdict (Q4).
- *Placed in PLAN.md (driver kit skeleton), agreed with the user:* confirm by trial (before the Claude Code kit is built): PreToolUse `updatedInput` can inject `session_id` into an engine MCP tool's arguments (Q3). If it cannot, fall back to binding every registered session of the same user on the same subject.
- *Done in verify:* add `subject.key` to the definition schema (`docs/definitions/schema.md`, `sdlc-reference.yaml`) and its semantic rules: every listed input exists in `run_inputs` (Q1).
- *Added to BACKLOG "Next" (Observed driver capabilities):* compare declared capabilities with observed behaviour, for example `enforcement` declared but no gate calls (Q9).

## Changelog

- 2026-10-21 Created.
- 2026-10-21 Briefing and questions Q1–Q10 prepared by spike-researcher; status in-progress.
- 2026-10-21 Q1–Q4 answered.
- 2026-10-21 Q5, Q6, Q7, Q10 answered; G6 answered; Q11 added.
- 2026-10-21 Q8, Q9, Q11 answered; G1–G5 answered; recommendation and `docs/driver-api/` drafted for agreement.
- 2026-10-21 Verified and closed: deliverables ticked; BACKLOG (spike 8, MCP server, Hooks to engine, Result delivery, Runs and repositories, Stage tasks, Next) and PLAN updated; `subject.key` added to the schema (rule 19) and the reference workflow; glossary, sequences, and architecture README aligned.
