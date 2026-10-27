---
id: 006
title: "Claude Code telemetry"
type: spike
status: done
phase: 0
depends_on: []
refs: ["PLAN Phase 0 / Spikes / 5. Claude Code telemetry", "BACKLOG Spikes / Wave 2 / 5. Claude Code telemetry", "BACKLOG MVP / Telemetry pipeline", "BACKLOG MVP / Session correlation", "BACKLOG Happy path / 8", "BACKLOG Happy path / 9"]
updated: 2026-10-19
---

# 006 · Claude Code telemetry

<!--
A spike answers questions and settles decisions; it does not ship production code. Run it with /sdd-spike 006:
the spike-researcher agent prepares a briefing and questions, you answer and add information over as many sessions
as it takes, and everything learned is recorded here. Prototypes are throwaway and live under specs/006-*/prototype/.
Progress counts the checkboxes in Goal questions, Questions for you, and Deliverables.
-->

## Inbox

<!-- Drop notes, links, constraints, or decisions here at any time, in any form. The next /sdd-spike session moves them into the Log and Findings and clears this section. -->

## Why

Happy path steps 8 and 9 need Claude Code's OpenTelemetry output to reach a collector, be correlated with the run by session ID, and show per-run tokens, duration per stage, and tool errors in the UI. Before the pipeline and the quality view are designed, we need to know what the export actually contains and how a driver kit turns it on.

It settles the `BACKLOG.md` MVP items Telemetry pipeline and Session correlation. Its deliverable is a write-up of what the quality view can and cannot show. Spike 7 (target architecture) depends on it. It shares a prototype with spike 4 (driver feasibility, spec 005).

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [x] **G1** Which metrics and events does Claude Code's OpenTelemetry export contain, with which attributes?
- [x] **G2** Is the session ID present on every metric and event, and is it the same ID a session-start hook sees, so the engine can join telemetry to a run?
- [x] **G3** What do tokens and cost look like: per model, per request, input/output/cache split, and what does cost mean on a subscription?
- [x] **G4** What does it say about tool calls: tool name, success or error, duration, and whether results or prompts are included (and how to redact them)?
- [x] **G5** How is the export configured from a driver kit (plugin, settings file, or environment), and can a plugin or hook add attributes such as a run ID?
- [x] **G6** Does telemetry cover subagents and runner-side headless evaluators, and can it tell them apart from the main session?
- [x] **G7** Which of the measures the intent asks for (tokens by stage, agent, and skill; latency; stage duration; retries; tool errors; test outcomes) can the quality view show from telemetry, which must come from engine events, and which are unavailable?

## Briefing

Prepared by the spike-researcher agent on 2026-10-19 from the repository and the current Claude Code docs (which mention versions up to v2.1.296). Nothing was run yet: every behavior below is from the docs and is unconfirmed until a real session is captured.

### What the repository says

- **G1:** spike 001 already relies on `claude_code.token.usage` and `claude_code.cost.usage`. The intent wants the OTel output to reach "a collector associated with the engine" (INTENT Observability). Suggested: a collector forwards to the engine, which keeps only tokens, cost, duration, tool results, and errors, keyed by session ID; a Grafana-style stack is the alternative (BACKLOG MVP / Telemetry pipeline).
- **G2:** the engine joins telemetry to run events by the session ID recorded at binding, within the session's time window on each run (INTENT Observability). Suggested: the session-start hook registers the ID; confirm it is in the telemetry (BACKLOG MVP / Session correlation).
- **G3:** the owner is on Max 5x. Runner evaluators use the owner's `setup-token` token while the owner is the only user (BACKLOG Runner / Model access).
- **G4:** activity recording is suggested as a post-tool-use hook that sends tool name and outcome, with no transcripts yet (BACKLOG MVP / Activity recording). Secrets and sensitive prompts are redacted "according to policy" (INTENT Observability); the policy does not exist yet.
- **G5:** the kit is a one-step installable plugin (INTENT Claude Code driver kit). `telemetry` is a declarable driver capability (`docs/definitions/schema.md`, `required_capabilities`). How the host reaches the collector is spike 10's (BACKLOG spike 10, Networking).
- **G6:** the MVP builds no driver-side evaluator subagent (BACKLOG Evaluator location). Reasoning steps run as headless Claude Code in runner sandboxes; `code_quality` has a council of 3. The intent wants tokens "including runner-side and driver-side evaluators". Sandbox egress is suggested to be only an in-cluster Maven proxy.
- **G7:** milestone 6 is done when "a run's stages, tokens, and latency can be reconstructed without asking the driver agent; unavailable metrics are explicit" (INTENT milestone 6). Hidden acceptance tests are measured after the run (`docs/test-bed.md`).

### Researched

Sources: [monitoring](https://code.claude.com/docs/en/monitoring-usage), [env-vars](https://code.claude.com/docs/en/env-vars), [hooks](https://code.claude.com/docs/en/hooks), [cli-reference](https://code.claude.com/docs/en/cli-reference), [plugins-reference](https://code.claude.com/docs/en/plugins-reference), [costs](https://code.claude.com/docs/en/costs), [statusline](https://code.claude.com/docs/en/statusline), [headless](https://code.claude.com/docs/en/headless), checked 2026-10-19; [OpenTelemetry Collector](https://github.com/open-telemetry/opentelemetry-collector) v0.163.1 (2026-10-16).

**G1 · Signals.**
- Enabled by `CLAUDE_CODE_ENABLE_TELEMETRY=1` plus `OTEL_METRICS_EXPORTER` (`otlp|prometheus|console|none`) and `OTEL_LOGS_EXPORTER` (`otlp|console|none`). `OTEL_EXPORTER_OTLP_PROTOCOL` (`grpc|http/json|http/protobuf`) has no default and must be set.
- Intervals: metrics 60 s, logs 5 s, traces 5 s. Metric temporality defaults to delta. Content attributes are truncated at 60 KB.
- Metrics: `claude_code.session.count` (`start_type`), `claude_code.token.usage` (`type`: input, output, cacheRead, cacheCreation), `claude_code.cost.usage` (USD), `claude_code.lines_of_code.count`, `claude_code.commit.count`, `claude_code.pull_request.count`, `claude_code.code_edit_tool.decision`, `claude_code.active_time.total` (`type`: user, cli). Token and cost counters also carry `model`, `query_source` (`main|subagent|auxiliary`), `speed`, `effort`, `agent.name`, `skill.name`, `plugin.name`, `mcp_server.name`, `mcp_tool.name`.
- Events (OTel logs): `user_prompt`, `assistant_response`, `api_request`, `api_error`, `api_retries_exhausted`, `api_refusal`, `tool_result`, `tool_decision`, `subagent_completed`, `skill_activated`, `compaction`, `hook_execution_start`/`_complete`, `mcp_server_connection`, `plugin_loaded`, `auth`, `permission_mode_changed`, `internal_error`. They carry `prompt.id`, `event.sequence` (per process, not per session), `event.timestamp`, and `message.uuid`, `request_id`, `tool_use_id` (which may change between versions).
- Resource attributes: `service.name=claude-code`, `service.version`, `os.type`, `os.version`, `host.arch`. Opt-in labels: `app.version`, `app.entrypoint`, `vcs.repository.*` (v2.1.269+).
- Traces are beta (`CLAUDE_CODE_ENHANCED_TELEMETRY_BETA=1` + `OTEL_TRACES_EXPORTER`): spans `claude_code.interaction` → `llm_request` (with `ttft_ms`), `tool`, `tool.blocked_on_user`, `tool.execution`; `agent_id`/`parent_agent_id` nest subagents.
- Name clash: `workflow.run_id`/`workflow.name` belong to Claude Code's own Workflow tool, not to our runs.

**G2 · Session ID.**
- `session.id` is a standard attribute on all metrics, events, and spans; on metrics it is controlled by `OTEL_METRICS_INCLUDE_SESSION_ID`, default true.
- `CLAUDE_CODE_SESSION_ID` in hook, Bash, and stdio MCP subprocesses "matches the `session_id` field in the hook JSON input and is updated on `/clear`". The docs imply, but never state, that OTel `session.id` is the same value.
- `/clear` assigns a new `session.id`; `--resume` keeps it unless `--fork-session`; SessionStart `source` is `startup|resume|clear|compact|fork`. `--session-id <uuid>` lets the caller choose the ID.
- `tool_result.tool_use_id` matches the hook's `tool_use_id`, so OTel events join to hook-reported activity.

**G3 · Tokens and cost.**
- Per request, `api_request` carries `model`, `input_tokens` (excludes cache), `output_tokens`, `cache_read_tokens`, `cache_creation_tokens`, `cost_usd`, `duration_ms`, `request_id`, `query_source`, `speed`, `effort`.
- Cost is a client-side approximation at list price. On Pro and Max "the session cost figure isn't relevant for billing purposes". Plan-limit usage (`rate_limits.five_hour`/`seven_day.used_percentage`) is only in status line JSON and `/usage`, not in OTel.
- `claude -p --output-format json` returns `session_id`, `usage`, `total_cost_usd`, and a per-model breakdown.

**G4 · Tool calls and content.**
- `tool_result` carries `tool_name`, `tool_use_id`, `success`, `duration_ms`, `error_type`, `decision_source`, input and result sizes. Rejected calls produce only `tool_decision`.
- Off by default: `OTEL_LOG_USER_PROMPTS` (text in `prompt` and `prompt_text`), `OTEL_LOG_ASSISTANT_RESPONSES`, `OTEL_LOG_TOOL_DETAILS` (adds `tool_parameters`, `tool_input` ~4 KB, full `error`, and real agent, skill, plugin, and MCP names), `OTEL_LOG_TOOL_CONTENT` (spans), `OTEL_LOG_RAW_API_BODIES`.
- Without `OTEL_LOG_TOOL_DETAILS`, tools from user-configured MCP servers, such as the engine's, show as `mcp_tool`.
- `user.email`, `user.account_uuid`, and `organization.id` are on every signal when signed in with OAuth.

**G5 · Configuration.**
- OTel exporter and content variables are ignored in project and local settings (which can only switch a signal off). They work from the shell, `~/.claude/settings.json` `env`, managed settings, and probably `--settings`. They are not passed to hooks or Bash.
- A plugin's `settings` applies only `agent` and `subagentStatusLine`: **a plugin cannot turn telemetry on**.
- `OTEL_RESOURCE_ATTRIBUTES` (`key=value`, comma-separated, percent-encoded) is read once at startup, so a hook cannot add a run ID afterwards. It is added to metric labels by default.
- `otelHeadersHelper` prints JSON headers at startup and every 29 minutes; `http/*` protocols only; exports stop if it fails.
- Collector: OTLP receiver on gRPC `:4317` and HTTP `:4318`; `otlp_http` exporter appends `/v1/logs|metrics|traces` (`encoding: json` available); `debug` exporter with `verbosity: detailed`; contrib `attributes` (delete) and `filter` (OTTL) processors for redaction and dropping.

**G6 · Subagents and headless.**
- Subagent requests carry `query_source="subagent"` and `agent.name`; third-party plugin agents show as `custom` or `third-party` unless `OTEL_LOG_TOOL_DETAILS=1`. `subagent_completed` carries `agent_type`, `total_tool_uses`, `duration_ms`, `model`; its `total_tokens` covers only the final request.
- Skill names: user-defined skills are verbatim on metrics; third-party plugin skills show as `third-party` on metrics and as `custom_skill` on `skill_activated` without the flag.
- `-p` reads `TRACEPARENT`; interactive sessions ignore it. `--bare` "never reads OAuth credentials"; the docs' example uses `ANTHROPIC_API_KEY` (relevant to spike 4's runner evaluator).


## Questions for you

- [x] **Q1** (G5, G2) Where should the driver kit turn on telemetry on the Mac, given that a plugin cannot? Options: an `env` block in `~/.claude/settings.json` written by a kit setup step; a launcher command that sets the env only for driver sessions; a managed settings file; the shell profile. *Suggested:* `~/.claude/settings.json`; the engine discards sessions never bound to a run.
- [x] **Q2** (G2, G7) Should one run accept several Claude Code sessions, and one session serve several runs over time? `/clear` creates a new `session.id`, so a 1:1 binding loses telemetry after a `/clear`. Options: 1:1, re-binding refused; many-to-many with binding intervals, re-bound by the SessionStart hook on every `source`. *Suggested:* many-to-many with intervals.
- [x] **Q3** (G4, G6, G7) Should `OTEL_LOG_TOOL_DETAILS` be on, with the collector deleting content attributes? Without it, the kit's skills, agents, and engine MCP calls show as `third-party`, `custom`, or `mcp_tool`; with it, Bash commands, paths, and error strings leave the laptop. Options: off; on, with the collector deleting `tool_input`, `tool_parameters`, and `error`; on, keeping everything. *Suggested:* on, with the collector deleting them.
- [x] **Q4** (G4) Should prompt and response text stay out of telemetry in the MVP? Options: off (lengths only); prompts only; prompts and responses. *Suggested:* off; no happy path step needs the text.
- [x] **Q11** (G2) A session always belongs to one run (Q2). What happens if you start a second run from a session that already belongs to a finished run? Options: the engine refuses and asks you to `/clear` (which gives a new session ID); the engine refuses until you open a new Claude Code session; the session moves to the new run once its old run is finished (which breaks "always owned by one run"). *Suggested:* refuse and ask for `/clear`, since `/clear` already gives a fresh ID that the SessionStart hook binds.
- [x] **Q12** (G4) Prompts and responses are kept (Q4). Should the collector scan that text for secrets before forwarding, or keep it as is while you are the only user? Options: keep as is, recorded as an accepted risk; redact common secret patterns (tokens, keys) with a collector processor; keep it out of the engine database and only in the collector's raw file. *Suggested:* redact common secret patterns, because the intent asks for secrets to be redacted and a regex processor costs little.
- [x] **Q13** (G5) The collector accepts telemetry only with the driver token (Q10). Who checks the token? Options: the collector passes the header on and the engine checks it on every forwarded batch; the collector checks it against the engine through an auth extension; the collector checks one static token from its config. *Suggested:* the engine checks it, because the engine already owns driver tokens and the collector needs no custom extension.
- [x] **Q5** (G3, G7) What should the run page show for cost on a subscription? `cost_usd` is a list-price estimate. Options: tokens only; tokens plus estimated cost labelled "API-equivalent estimate"; estimated cost as the headline. *Suggested:* tokens first, with the labelled estimate next to them.
- [x] **Q6** (G3) Record each run's share of the 5-hour and weekly Max limits? Only a status line script sees them, and a plugin cannot install one. Options: skip in the MVP; a status line script that posts `rate_limits` to the engine; read `/usage` by hand during spike 4. *Suggested:* skip, and read `/usage` by hand in spike 4.
- [x] **Q7** (G6, G3) How should runner-side evaluators report usage? Options: the runner returns `usage`/`total_cost_usd` from `-p --output-format json` with the job result; OTel export from the sandbox, with `--session-id` and `OTEL_RESOURCE_ATTRIBUTES` carrying run, step, and council IDs; both. *Suggested:* the JSON result; it needs no extra sandbox network access.
- [x] **Q8** (G1, G7) Should the collector forward only to the engine, or also to a raw store? Options: engine only (OTLP/HTTP JSON); engine plus a file exporter; engine plus a Grafana-style stack. *Suggested:* engine plus a file exporter while developing.
- [x] **Q9** (G1, G7) Events only, or also metrics and beta traces? Per-request events carry timestamps and every needed field; traces add time to first token and the subagent tree. Options: events only; events plus metrics; all three. *Suggested:* events only, with metrics exported but unused.
- [x] **Q10** (G5) Does the collector endpoint need authentication in the MVP? Options: none, bound to localhost; the driver token as a static header; a rotating header via `otelHeadersHelper` (`http/*` only). *Suggested:* none, bound to localhost while the owner is the only user, recorded as an accepted risk to revisit before GKE.

- [x] **Q14** (G7) What does the intent's "retries" mean on the quality view? Options: API retries (from telemetry); failed checkpoint attempts and rework loops (from engine events); both, shown as separate measures. *Suggested:* both, separately, because they measure different things (provider flakiness against work quality).
- [x] **Q15** (G7) Do you agree with the draft split in Findings G7 of what the quality view shows from telemetry, from telemetry joined with engine windows, from engine events only, and what is unavailable?

## Log

- 2026-10-19 · research · spike-researcher briefing from the Claude Code monitoring, env-vars, hooks, CLI, plugins, costs, statusline, and headless docs, and the OTel Collector docs. Key facts: a plugin cannot enable telemetry; resource attributes are fixed at startup, so a run ID cannot be added after `start_run`; `/clear` assigns a new `session.id`; cost on Max is a list-price estimate and plan-limit usage is not in OTel; the runner can choose the session ID with `--session-id`; `--bare` does not read OAuth credentials (passed on to spike 4). Nothing run yet.
- 2026-10-19 · answer to Q1 · Telemetry is enabled through an `env` block in `~/.claude/settings.json`, written by a kit setup step. Every session exports; the engine discards sessions never bound to a run.
- 2026-10-19 · answer to Q2 · "one run has many sessions, but a session is always owned by a run". A run can have several Claude Code sessions (for example after `/clear`); a session belongs to exactly one run and never moves to another. Raised Q11.
- 2026-10-19 · answer to Q3 · `OTEL_LOG_TOOL_DETAILS` on; the collector deletes `tool_input`, `tool_parameters`, and `error` before forwarding.
- 2026-10-19 · answer to Q4 · Prompts and responses are kept in telemetry in the MVP (`OTEL_LOG_USER_PROMPTS` and `OTEL_LOG_ASSISTANT_RESPONSES` on). The collector therefore must not delete `prompt`/`prompt_text`. Raised Q12 (secret redaction in that text).
- 2026-10-19 · answer to Q11 · A session already owned by a run cannot start another run: the engine refuses and asks for `/clear`, which gives a new session ID that the SessionStart hook binds to the new run.
- 2026-10-19 · answer to Q12 · The collector redacts common secret patterns (API keys, tokens, private keys) in prompt and response text before forwarding.
- 2026-10-19 · answer to Q5 · The run page shows tokens first, with the cost next to them labelled "API-equivalent estimate".
- 2026-10-19 · answer to Q6 · Plan-limit usage is not recorded in the MVP; spike 4 reads `/usage` by hand during its pilot runs.
- 2026-10-19 · note · Main's new constitution article 9 (need no orchestrator) was checked against this spike: telemetry is pulled into the engine by the collector and never pushes work to a driver, so nothing changes. (Merging main into this branch is blocked on the app's pinned-origin check.)
- 2026-10-19 · answer to Q7 · Runner evaluators report usage in the job result, from `claude -p --output-format json` (`usage`, `total_cost_usd`). Sandboxes do not export OTel, so they need no egress to the collector.
- 2026-10-19 · answer to Q8 · The collector forwards to the engine and also writes a raw file while developing.
- 2026-10-19 · answer to Q9 · The MVP uses events, metrics, and traces. Traces are beta and need `CLAUDE_CODE_ENHANCED_TELEMETRY_BETA=1` and `OTEL_TRACES_EXPORTER=otlp`.
- 2026-10-19 · answer to Q10 · The collector endpoint requires the driver token as a static OTLP header (`OTEL_EXPORTER_OTLP_HEADERS`). The token then sits in the `env` block of `~/.claude/settings.json` (Q1). Raised Q13 (who checks it).
- 2026-10-19 · answer to Q13 · The collector passes the token header on; the engine checks the driver token on every forwarded batch. The collector needs no custom auth extension.
- 2026-10-19 · note · The user approved running the capture prototype now: a hand-written OTLP/HTTP JSON receiver and short headless sessions, with telemetry env set only on those processes.
- 2026-10-19 · research (prototype) · Captured five headless `claude -p` sessions (v2.1.302, haiku) with a hand-written OTLP/HTTP JSON receiver (`prototype/`). Confirmed: one session ID across hooks, env, and all OTel signals; `--resume` keeps it; event token sums equal metric totals; `-p` `modelUsage` matches telemetry while `usage` leaves out subagents, and resumed results are cumulative; project-settings `env` does not enable export but `--settings` does; the auth header and `OTEL_RESOURCE_ATTRIBUTES` arrive on every signal; `OTEL_LOG_TOOL_DETAILS` adds `tool_input`, `tool_parameters`, `error`, and `full_command`. Contradicts the docs: project subagents show as `custom` without the detail flag. Also seen: a subagent hand-back arrives as a `user_prompt`, and the Stop hook fired while a subagent was still running. Not covered: `/clear`, MCP tool naming, user-settings `env`, edits (lines and commits), and collector redaction.
- 2026-10-19 · answer to Q14 · "Retries" means both, shown as separate measures: API retries (telemetry) and failed checkpoint attempts and rework loops (engine events).
- 2026-10-19 · answer to Q15 · The user agreed to the G7 split of what the quality view shows.
- 2026-10-19 · note · The user agreed to the recommendation table.

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1 · Signals

- **Fact** (capture, v2.1.302): all three signals arrive over OTLP/HTTP JSON at `/v1/logs`, `/v1/metrics`, and `/v1/traces`, with scopes `com.anthropic.claude_code.events`, `com.anthropic.claude_code`, and `com.anthropic.claude_code.tracing`. Resource attributes: `service.name=claude-code`, `service.version`, `os.type`, `os.version`, `host.arch`, plus anything in `OTEL_RESOURCE_ATTRIBUTES`.
- **Fact** (capture): metrics seen are `claude_code.session.count`, `token.usage`, `cost.usage`, and `active_time.total`, all monotonic sums with delta temporality. `lines_of_code.count`, `commit.count`, and `pull_request.count` did not appear, because the sessions made no edits (docs list them).
- **Fact** (capture): events seen are `user_prompt`, `assistant_response`, `api_request`, `tool_decision`, `tool_result`, `subagent_completed`, `skill_activated`, `hook_registered`, `hook_execution_start`/`_complete`, `plugin_loaded`, `mcp_server_connection`, and `managed_settings_resolved`. Every event also carries `user.id`, `user.email`, `user.account_uuid`, `user.account_id`, `organization.id`, and `terminal.type`.
- **Fact** (capture): spans are `claude_code.interaction` → `llm_request`, `tool` → `tool.blocked_on_user`, `tool.execution`. `llm_request` carries tokens, `attempt`, `success`, and `request_id`; `tool` spans carry `tool_name`, `bash_argv0`, and `bash_command_class`.
- **Fact** (capture): a short `claude -p` session flushed all its events, metrics, and spans before exit.
- **Decision** (Q8): the collector forwards to the engine and also writes a raw file while developing.
- **Decision** (Q9): the MVP ingests events, metrics, and the beta traces (`CLAUDE_CODE_ENHANCED_TELEMETRY_BETA=1`, `OTEL_TRACES_EXPORTER=otlp`).

### G2 · Session correlation

- **Fact** (capture): the hook's `session_id`, `CLAUDE_CODE_SESSION_ID` in the hook process, and the OTel `session.id` on every metric, event, and span are the same value. Spans also repeat it as `gen_ai.conversation.id`.
- **Fact** (capture): `--resume` keeps the session ID (SessionStart `source=resume`), and `--session-id <uuid>` sets it.
- **Fact** (docs): `/clear` assigns a new session ID and updates `CLAUDE_CODE_SESSION_ID`; SessionStart then fires with `source=clear`. Not captured, because `/clear` is interactive only.
- **Fact** (capture): `tool_result.tool_use_id` equals the `tool_use_id` in PostToolUse and PostToolUseFailure hook input, so OTel tool events join to hook-reported activity.
- **Fact** (docs, consistent with the capture): a laptop session's resource attributes are fixed at startup, so a run ID cannot be added after `start_run`. The engine joins on `session.id` within the binding window.
- **Decision** (Q2): one run has many sessions; a session is always owned by exactly one run. The SessionStart hook binds each new session ID (startup, `/clear`, fork) to the current run; a resumed session keeps its run.
- **Decision** (Q11): a session already owned by a run cannot start another run; the engine refuses with a message asking for `/clear`.

### G3 · Tokens and cost

- **Fact** (capture): `api_request` carries `model`, `input_tokens`, `output_tokens`, `cache_read_tokens`, `cache_creation_tokens` (split into `_5m` and `_1h`), `cost_usd`, `cost_usd_micros`, `duration_ms`, `ttft_ms`, `request_id`, `query_source`, `speed`, `effort`, and `agent.name` or `skill.name` where they apply.
- **Fact** (capture): per session, the summed `api_request` tokens equal the `token.usage` metric totals exactly, by type (for example 16 input, 1340 output, 143911 cache read, 20558 cache creation).
- **Fact** (capture): in `claude -p --output-format json`, `modelUsage` (per model: tokens, `costUSD`, `costBasis: "list"`) matches the telemetry totals including subagents. The top-level `usage` covers the main thread only and leaves out subagent tokens. After `--resume`, the result's totals cover the whole session so far, not just that invocation.
- **Fact** (docs): on Max, cost is a list-price estimate, not a bill; plan-limit usage is not in OTel.
- **Decision** (Q5): the run page shows tokens first, with cost labelled "API-equivalent estimate".
- **Decision** (Q6): plan-limit usage is not recorded in the MVP; spike 4 reads `/usage` by hand.
- **Decision** (Q7): runner evaluators report usage in the job result. The capture refines this: read `modelUsage`, not `usage`.

### G4 · Tool calls and content

- **Fact** (capture): with the detail flags off, `tool_result` carries `tool_name`, `tool_use_id`, `success`, `duration_ms`, `error_type` (for example `ShellError`), and input and result sizes. Prompt and response text arrive as `<REDACTED>` with their length.
- **Fact** (capture): `OTEL_LOG_TOOL_DETAILS=1` adds `tool_input`, `tool_parameters`, and `error` to `tool_result`; `tool_parameters` to `tool_decision`; `full_command` (the whole Bash command), `skill_name`, and `subagent_type` to `claude_code.tool` spans; and `error` and `server_name` to `mcp_server_connection`. The `claude_code.tool.execution` span carries an `error` attribute in both modes.
- **Fact** (capture): `OTEL_LOG_USER_PROMPTS=1` and `OTEL_LOG_ASSISTANT_RESPONSES=1` put the text in `prompt`/`prompt_text` on `user_prompt`, `response` on `assistant_response`, and `user_prompt` on the `claude_code.interaction` span.
- **Fact** (docs, not captured): without `OTEL_LOG_TOOL_DETAILS`, tools from user-configured MCP servers show as `mcp_tool`. No MCP tool was called in the capture.
- **Decision** (Q3): `OTEL_LOG_TOOL_DETAILS` is on. The collector deletes `tool_input`, `tool_parameters`, `error`, and `full_command` before forwarding, so names survive and command text and paths do not reach the engine.
- **Decision** (Q4): prompt and response text is kept.
- **Decision** (Q12): the collector redacts common secret patterns in prompt and response text before forwarding.

### G5 · Configuration

- **Fact** (capture): telemetry `env` in a project's `.claude/settings.json` does not enable export (no signal arrived), while the same `env` passed through `--settings` does. Variables set on the process work.
- **Fact** (docs, not captured): `env` in `~/.claude/settings.json` and managed settings also enable export; a plugin's settings cannot.
- **Fact** (capture): `OTEL_EXPORTER_OTLP_HEADERS="Authorization=Bearer …"` arrives as an `Authorization` header on every signal.
- **Fact** (capture): `OTEL_RESOURCE_ATTRIBUTES=run.id=…` appears as a resource attribute on all signals, and as a label on every metric data point and an attribute on every event and span.
- **Decision** (Q1): a kit setup step writes the telemetry `env` block into `~/.claude/settings.json`. Every session exports; the engine discards sessions never bound to a run.
- **Decision** (Q10): the collector requires the driver token as a static header, set through `OTEL_EXPORTER_OTLP_HEADERS` in the same `env` block.
- **Decision** (Q13): the collector passes the token header on, and the engine checks the driver token on every forwarded batch.

### G6 · Subagents and runner evaluators

- **Fact** (capture): subagent requests have `query_source=agent:<name>` on events and `query_source=subagent` on metrics; main-thread requests in `-p` show `sdk` on events and `main` on metrics. A skill's requests carry `skill.name` on both.
- **Fact** (capture, which contradicts the docs): with details off, a project-defined subagent shows as `custom` on events, metrics, and `subagent_completed`, not under its own name. A project skill keeps its name on `api_request` and metrics, but shows as `custom_skill` on `skill_activated`. With `OTEL_LOG_TOOL_DETAILS=1`, both show their real names everywhere.
- **Fact** (capture): in this setup a subagent's result came back as a new `user_prompt` (text starting with `<agent-message`), and the Stop hook fired while the subagent was still running.
- **Fact** (capture): headless `claude -p` exports the same signals as an interactive session, with `terminal.type=non-interactive`. `--session-id` and `OTEL_RESOURCE_ATTRIBUTES` stamp a runner job if it ever exports.
- **Decision** (Q3): `OTEL_LOG_TOOL_DETAILS` is on, so the kit's agent and skill names appear verbatim.
- **Decision** (Q7): runner evaluators report usage in the job result (`modelUsage` from `-p --output-format json`); sandboxes do not export OTel.

### G7 · What the quality view can show

- **Decision** (Q15, the split agreed with the user):
  - From telemetry: tokens by model and type, and by subagent and skill; estimated cost; API latency and time to first token (`ttft_ms` on `api_request`); API errors and retries (`api_error`, `api_retries_exhausted`, span `attempt`); tool calls, errors, and durations; permission decisions; compactions; active time; commits and lines changed (docs only).
  - Telemetry joined with engine windows: tokens, cost, and tool errors by stage (each event's timestamp matched to a stage visit of the bound session).
  - Engine events only: stage duration, checkpoint attempts and failures, rework loops, approval wait time, overrides, test outcomes (runner checks and hidden acceptance tests), and runner evaluator tokens (from job results).
  - Unavailable: stage or run identity inside the laptop telemetry, plan-limit usage, and a clean count of human prompts, because subagent hand-backs (and Stop-hook continuations, still to check in spike 4) also produce `user_prompt` events.
- **Decision** (Q14): "retries" is two separate measures: API retries from telemetry, and failed checkpoint attempts and rework loops from engine events.

## Recommendation and decisions

Agreed with the user on 2026-10-19.

| Decision | Choice | Alternatives considered | Why |
| --- | --- | --- | --- |
| Turning telemetry on | A kit setup step writes the telemetry `env` block into `~/.claude/settings.json` | Launcher command; managed settings; shell profile | A plugin and project settings cannot enable it (docs; project scope confirmed by capture). User settings need no wrapper; the engine drops unbound sessions (Q1) |
| Signals | Events, metrics, and beta traces over OTLP/HTTP JSON | Events only; events plus metrics | The user wants all three (Q9). Events alone carry every needed field; metrics and spans add cross-checks and the span tree |
| Run and session binding | One run has many sessions; a session belongs to exactly one run; SessionStart binds each new ID; starting a second run in a bound session is refused with a request to `/clear` | One-to-one; many-to-many | `/clear` gives a new ID, and the user wants each session owned by one run (Q2, Q11) |
| Correlation key | `session.id` within each binding window; no run ID in laptop telemetry | `OTEL_RESOURCE_ATTRIBUTES` with a run ID | The ID is identical across hooks and all signals (capture); resource attributes are fixed at startup |
| Content flags | `OTEL_LOG_TOOL_DETAILS`, `OTEL_LOG_USER_PROMPTS`, and `OTEL_LOG_ASSISTANT_RESPONSES` on | Details off; prompts off | Without details, even project subagents show as `custom` (capture). The user wants prompt and response text (Q3, Q4) |
| Collector processing | Delete `tool_input`, `tool_parameters`, `error`, and `full_command`; redact secret patterns in prompt and response text; forward to the engine and to a raw file while developing | Keep everything; engine only; Grafana-style stack | Names survive while command text and paths stay out; secrets are redacted as the intent asks (Q3, Q8, Q12) |
| Collector auth | The driver token as a static `Authorization` header; the collector passes it on and the engine checks it per batch | No auth on localhost; rotating `otelHeadersHelper`; collector-side check | The header arrives on every signal (capture); the engine already owns driver tokens (Q10, Q13) |
| Runner evaluator usage | The runner returns `modelUsage` from `claude -p --output-format json` in the job result; sandboxes do not export OTel | OTel from sandboxes; both | It is per job and complete including subagents, while `usage` is not (capture); no sandbox egress to the collector (Q7) |
| Cost on the run page | Tokens first, with cost labelled "API-equivalent estimate" | Tokens only; cost headline | Cost is client-side at list price (`costBasis: "list"`), not what Max charges (Q5) |
| Plan-limit usage | Not recorded in the MVP; spike 4 reads `/usage` by hand | Status line script posting `rate_limits` | Only a status line sees it, a plugin cannot install one, and it is not on the happy path (Q6) |
| Quality view sources | As agreed in Findings G7; "retries" is API retries and checkpoint retries, shown separately | — | Agreed with the user (Q14, Q15) |

## Deliverables

- [x] Every goal question answered, and the recommendation agreed with the user
- [x] BACKLOG.md items settled by this spike updated

## Follow-ups

Run on 2026-10-19 (see Log): the session capture, the config scope check (project settings and `--settings`; user settings not tested), the headless check (on the host, not in a Linux container), and the token reconciliation.

Still open, recorded on 2026-10-19 as inputs in `BACKLOG.md`. Spike 4 closed in parallel (spec 005), so its share went to the MVP items instead: Hooks to engine (`/clear`, subagent hand-back, Stop hook), Telemetry pipeline (MCP tool names), and Model access (`modelUsage`). Spec 005 settled `--bare`: the runner must not use it. Spike 7: ingestion rules. Spike 10: collector processors and exporters.

- Manual check: one interactive session with `/clear`, to confirm the new session ID in telemetry and SessionStart `source=clear`. About 10 minutes.
- Manual check: an engine MCP tool call with `OTEL_LOG_TOOL_DETAILS` on, to confirm the server and tool names. Fits in spike 4's MCP prototype.
- Collector redaction: an `attributes` processor that deletes `tool_input`, `tool_parameters`, `error`, and `full_command`, plus a secret-pattern redaction of `prompt`, `prompt_text`, `response`, and the `user_prompt` span attribute. This belongs to the collector scaffolding (spike 10 or Phase 0 scaffolding).
- For spike 4 (spec 005): a subagent's hand-back arrived as a new `user_prompt`, and the Stop hook fired while the subagent was still running. Check this against the Stop-hook stage task design. Also, `--bare` may not accept a `setup-token` OAuth token.
- For spike 7: the engine drops sessions that were never bound, checks the driver token on each forwarded batch, and joins on `session.id` within binding windows.

Original list from the research:

- Capture one real session with a local collector (contrib v0.163.1, OTLP receiver, `debug` detailed and `file` exporters). Exercise `/clear`, `--resume`, a subagent, a plugin skill, an engine MCP call, and a failing Bash, with `OTEL_LOG_TOOL_DETAILS` off then on, and a SessionStart hook logging `session_id`. Settles G1, G2, G4, G6. About 2–3 hours.
- Check which config scopes enable export: `~/.claude/settings.json` `env`, `--settings` inline JSON, project `.claude/settings.json` (expected ignored). Settles G5. About 30 minutes.
- Headless in a Linux container (with spike 4): `claude -p --session-id <uuid> --output-format json` with `CLAUDE_CODE_OAUTH_TOKEN` and `OTEL_RESOURCE_ATTRIBUTES=run.id=…`; check that short runs flush, whether `user.email` is present, and whether `--bare` accepts the OAuth token. About 1–2 hours.
- Reconcile summed `api_request` tokens and `cost_usd` with `/usage` and the `-p` JSON `usage`. About 30 minutes.
- Check whether a Stop-hook continuation emits a `user_prompt` event (decides whether human prompts measure human intervention). About 15 minutes, during spike 4's Stop-hook prototype.
- Collector redaction: an `attributes` processor deleting `tool_input`, `tool_parameters`, `error`, `prompt`, `prompt_text`, `user.email`; confirm agent, skill, and MCP names survive. About 30 minutes.
- For spike 4: `--bare` may not accept a `setup-token` OAuth token, so runner evaluators may need to run without it.

## Changelog

- 2026-10-19 Created.
- 2026-10-19 Briefing, questions Q1–Q10, and first findings from research; status in-progress.
- 2026-10-19 Answers to Q1–Q4 recorded; added Q11 and Q12.
- 2026-10-19 Answers to Q5, Q6, Q11, and Q12 recorded.
- 2026-10-19 Answers to Q7–Q10 recorded; added Q13.
- 2026-10-19 Q13 answered; capture prototype run; Findings rewritten from it; G1–G6 ticked.
- 2026-10-19 Q14 and Q15 answered; G7 ticked; recommendation agreed with the user. Ready for /sdd-verify.
- 2026-10-19 Verified and closed: every goal question answered, BACKLOG items Telemetry pipeline and Session correlation decided, follow-ups recorded as inputs to spikes 4, 7, and 10, PLAN spike 5 ticked.
