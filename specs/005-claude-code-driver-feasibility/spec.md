---
id: 005
title: "Claude Code driver feasibility"
type: spike
status: done
phase: 0
depends_on: []
refs: ["PLAN Phase 0 / Spikes / 4. Claude Code driver feasibility", "BACKLOG Spikes / Wave 2 / 4. Claude Code driver feasibility", "BACKLOG MVP / MCP server", "BACKLOG MVP / Hooks to engine", "BACKLOG MVP / Activity recording", "BACKLOG MVP / Runner / Model access"]
updated: 2026-10-19
---

# 005 · Claude Code driver feasibility

<!--
A spike answers questions and settles decisions; it does not ship production code. Run it with /sdd-spike 005:
the spike-researcher agent prepares a briefing and questions, you answer and add information over as many sessions
as it takes, and everything learned is recorded here. Prototypes are throwaway and live under specs/005-*/prototype/.
Progress counts the checkboxes in Goal questions, Questions for you, and Deliverables.
-->

## Inbox

<!-- Drop notes, links, constraints, or decisions here at any time, in any form. The next /sdd-spike session moves them into the Log and Findings and clears this section. -->

## Why

The driver design assumes that Claude Code, on a subscription, can be a driver: it reaches the engine through MCP, its hooks tell the engine about the session and its activity, a hook lets the engine authorize the merge, and a headless Claude Code in the runner can act as a fresh-context evaluator. None of this has been tried. This spike confirms or refutes each part with a throwaway prototype, and records what works, what does not, and the workarounds.

It settles the `BACKLOG.md` MVP items MCP server, Hooks to engine, and Activity recording, and the feasibility of the runner evaluator (Runner / Model access). Spike 5 (telemetry) can reuse its prototype. Spikes 7 (target architecture) and 8 (driver API contract) depend on it.

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [x] **G1** Claude Code connects to a remote MCP server written with the MCP Java SDK, over HTTP with a bearer token.
- [x] **G2** A session-start hook can register the Claude Code session ID with the engine.
- [x] **G3** A pre-tool-use hook can block a merge based on an engine response (failing closed when the engine is down), and a post-tool-use hook can report activity.
- [x] **G4** Headless Claude Code runs in a Linux container as a runner-side evaluator: authenticated with a `claude setup-token` token, read-only tools, a fresh session per task, and the result as structured JSON output.
- [x] **G5** All of the driver side (MCP configuration, hooks, instructions) can be packaged as a Claude Code plugin.
- [x] **G6** Stage tasks driven by hooks (input from spike 001): does a Stop hook's 8-block cap fire despite progress; does Stop fire on Esc; does an `http` Stop hook against a stub continue on block and let Claude stop on failure; and does `/goal` with a turn limit in its condition work.
- [x] **G7** Usage: a full run, including runner evaluations alongside the developer's own session, fits within subscription limits (Max 5x). Pilot test-bed tasks T01, T05, and T06 in plain Claude Code to see whether the planted failures fire and how much usage each task takes (input from spike 002).
- [x] **G8** Lower priority, since the MVP does not need it: an evaluator subagent with a fresh context and read-only tools can fetch a task and submit its result through MCP without the main agent relaying it.
- [x] **G9** No orchestrator (constitution Art. 9): can one Claude Code session move through several stages using only generic kit instructions plus `status`, with nothing workflow-specific in the kit? How does a later session resume the run after a long wait on a human step or the runner, when the original session may have ended?

## Briefing

<!-- Prepared by the spike-researcher agent: what the repository and current docs already say, with sources. -->

Prepared 2026-10-19. Current versions: Claude Code 2.1.303 (npm `latest`); MCP Java SDK 2.1.0 stable, 2.2.0-M1 also published (Maven, 2026-10-16).

Findings that go against what the spike assumed:

- `--bare` cannot use the runner token: it does not read `CLAUDE_CODE_OAUTH_TOKEN`, and it will become the default for `-p`. The runner evaluator must keep the cloned repository's hooks and MCP servers out with other flags.
- The session-start hook cannot be `http`: SessionStart allows only `command` and `mcp_tool` hooks, and `mcp_tool` hooks are skipped at launch.
- Hooks fail open by default. The merge hook fails closed only with `onFailure: "block"` (Claude Code ≥ 2.1.295).
- Spike 001's two Stop-hook questions are settled by the docs: the 8-block cap resets on every tool call, and Stop does not fire on a user interrupt.
- Runner sessions do not show in the laptop's `/usage`; the status line's plan-wide percentages do include them.

### G1 · Remote MCP over HTTP with a bearer token

- Known: the engine exposes a remote MCP server with operations `start_run` … `authorize_action` (INTENT, Driver integration). Server-side components run on Kubernetes; the stack stays open until spike 9 (CLAUDE.md, Current state).
- Suggested: MCP Java SDK over HTTP with a bearer token (BACKLOG MVP / MCP server); one static driver token per user (BACKLOG MVP / Auth).
- Unknown: whether a Java SDK server and Claude Code 2.1.30x work together in practice; how the prototype runs the server before a cluster exists.

### G2 · Session-start registration

- Known: a session-start hook binds the session to a run and loads status (INTENT, Claude Code driver kit). Session correlation by session ID is spike 5's concern.
- Suggested: hooks call the engine's HTTP API with a small script; no CLI yet (BACKLOG MVP / Hooks to engine).
- Unknown: what the hook does when no run exists yet; whether `session_id` changes on `/clear`, compaction, resume, and fork.

### G3 · Merge gate and activity reporting

- Known: `merge` is allowed only in `release`. `authorize_action` must match the revision the passed checkpoint reviewed (003 Q11; `docs/definitions/sdlc-reference.yaml`). An unreachable engine must not let a protected action through (constitution Art. 5). Hooks only enforce at the tool boundary (INTENT, Claude Code driver kit).
- Suggested: a post-tool-use hook sends the tool name and outcome; no transcripts (BACKLOG MVP / Activity recording). Offline buffering is not a priority (BACKLOG On record).
- Unknown: which command does the merge (`gh pr merge`, or `git merge` plus `git push`); what the activity hook does when the engine is down.

### G4 · Headless evaluator in a container

- Known: the runner's evaluator runs headless with the owner's `setup-token` while the owner is the only user (BACKLOG Runner / Model access; constitution Art. 4). Evaluators report raw results; the engine applies thresholds (Art. 6). `code_quality` has a council of 3, plus 1 run for `docs_updated`: about 4 headless sessions per checkpoint attempt (003 Findings).
- Suggested: one Kubernetes Job per runner job (BACKLOG Runner / Sandbox technology, settled by spikes 9 and 10).
- Unknown: the Mac has no container runtime yet (spike 10 picks one); which model evaluators use; whether they see the subject repository's `CLAUDE.md`.

### G5 · Plugin packaging

- Known: the kit is a versioned plugin with MCP config, hooks, instructions, and an optional evaluator subagent (INTENT, Claude Code driver kit). A plugin cannot raise the 8-block cap (001 Researched).
- Unknown: how the plugin passes the driver token to `http` hooks.

### G6 · Stop-hook stage tasks and `/goal`

- Known: stage tasks have neutral limits; the kit enforces them and the engine records each iteration. Only budget exhaustion escalates to the owner (001 Q24, Q26, Q28). A failing `http` Stop hook lets Claude stop (001 Researched).
- Unknown: whether the kit drives stage tasks with a Stop hook or with `/goal`; whether a turn limit written into a `/goal` condition holds.

### G7 · Usage on Max 5x

- Known: runs are driven on Max 5x; the task set is designed for coverage, not sized to the plan (002 Q9). The `sample-crm` baseline, including T06's planted exact-match tests, is specified in spec 004 but not built yet (`docs/test-bed.md`).
- Unknown: the usage cost per task and per checkpoint attempt.

### G8 · Driver-side evaluator subagent

- Known: an optional capability, not built in the MVP (BACKLOG Evaluator location; Next / Driver-side evaluator subagent). The working agent only launches the evaluator; it neither writes the prompt nor relays the result (INTENT, Fresh-context evaluation).

### Researched

All checked 2026-10-19.

**G1**

- Add a server with `claude mcp add --transport http <name> <url> --header "Authorization: Bearer …"`. In `.mcp.json`, `type: "http"` (`streamable-http` is an alias), with `${VAR}` and `${VAR:-default}` expanded in `url` and `headers`. An unset variable stays literal, with a warning in `claude mcp list`. A rejected static `Authorization` header marks the server failed, with no OAuth fallback. ([MCP](https://code.claude.com/docs/en/mcp))
- An unreachable HTTP server is retried 3 times at startup, then marked failed; a server that drops mid-session is reconnected up to 5 times with backoff. SSE is deprecated. `headersHelper` produces headers dynamically and reruns on 401/403. (same)
- Plugin servers register as `plugin:<plugin>:<server>`, with tools named `mcp__plugin_<plugin>_<server>__<tool>`. Hook matchers written against the bare server name never fire for them. (same)
- MCP Java SDK 2.1.0 is the latest stable; 2.2.0-M1 adds a stateless server for protocol 2026-07-28. 2.x tracks protocol 2025-11-25; 2.1.0 dropped 2024-11-05 from Streamable HTTP. Java 17 baseline. ([releases](https://github.com/modelcontextprotocol/java-sdk/releases); [Maven](https://repo1.maven.org/maven2/io/modelcontextprotocol/sdk/mcp/maven-metadata.xml))
- The core `mcp` module ships `HttpServletStreamableServerTransportProvider`, a plain servlet with no framework; its `contextExtractor` reads the `Authorization` header. The WebMVC and WebFlux transports moved to Spring AI 2.0+. ([server docs](https://java.sdk.modelcontextprotocol.io/latest-snapshot/server))
- Known issue: from 2.1.84, Claude Code failed to connect to POST-only stateless servers that answer GET with 405 (Spring AI WebMVC stateless); closed unfixed. Workaround: the stateful Streamable HTTP transport. ([anthropics/claude-code#39790](https://github.com/anthropics/claude-code/issues/39790))

**G2**

- SessionStart supports only `command` and `mcp_tool` hooks. `mcp_tool` hooks are skipped at launch, resume, and continue, and run only after `/clear` or compaction. So registering the session needs a command hook. ([hooks](https://code.claude.com/docs/en/hooks))
- Common input fields: `session_id`, `transcript_path`, `cwd`, `permission_mode`, `hook_event_name`, plus `agent_id` and `agent_type` inside subagents. SessionStart adds `source` (`startup`, `resume`, `clear`, `compact`, `fork`) and `model`. (same)
- SessionStart can return `additionalContext`, which loads status into Claude's context, and can append exports to `$CLAUDE_ENV_FILE` for later Bash commands. (same)
- Plugin hook processes receive `CLAUDE_PLUGIN_ROOT`, `CLAUDE_PLUGIN_DATA`, `CLAUDE_PROJECT_DIR`, and `CLAUDE_PLUGIN_OPTION_<KEY>`. `${user_config.KEY}` is substituted only in exec-form `args`; shell-form commands reject it. ([plugins reference](https://code.claude.com/docs/en/plugins-reference))

**G3**

- PreToolUse blocks with `hookSpecificOutput.permissionDecision: "deny"` plus a reason, or with exit code 2 (which wins over a JSON `allow`). Narrow it with `matcher: "Bash"` and `if: "Bash(gh pr merge *)"`. The `if` filter is best-effort: `bash -c '…'` or `/usr/bin/git` get past it. ([hooks](https://code.claude.com/docs/en/hooks); [permissions](https://code.claude.com/docs/en/permissions))
- A failing hook fails open by default: a crash, a non-0/2 exit, a non-2xx response, a connection error, or a timeout lets the tool run. `"onFailure": "block"` on a `command` or `http` hook blocks the call instead; it needs Claude Code ≥ 2.1.295 and does not apply to async hooks. (hooks, "Block the action when a hook fails")
- The default timeout is 600 s for command and http hooks, so a hung engine stalls the tool call unless a short timeout is set. (hooks)
- PostToolUse input: `tool_name`, `tool_input`, `tool_response`, `tool_use_id`, and optional `duration_ms`. PostToolUseFailure adds `error` and `is_interrupt`. Both fire inside subagents. (hooks; [sub-agents](https://code.claude.com/docs/en/sub-agents))
- `async: true` exists only on `command` hooks: no blocking, no timeout enforced, killed at `-p` teardown. `http` hooks are always synchronous. (hooks)

**G4**

- `--bare` does not read `CLAUDE_CODE_OAUTH_TOKEN` or any OAuth credentials, and "will become the default for `-p` in a future release". ([authentication](https://code.claude.com/docs/en/authentication); [headless](https://code.claude.com/docs/en/headless))
- Without `--bare`, `-p` runs the project's `.claude/settings.json` hooks and connects its `.mcp.json` servers, untrusted. The documented guard: `--setting-sources user`, `--settings '{"disableAllHooks":true}'`, and `--strict-mcp-config`. (permissions, "What runs before you trust a folder")
- `--restricted` (≥ 2.1.248) is meant for evaluation harnesses: no command-running tools or WebFetch, file tools confined to the working directories, and only managed settings and `--settings` loaded. `--safe-mode` skips `CLAUDE.md`, skills, plugins, hooks, and MCP while authentication works normally. ([CLI reference](https://code.claude.com/docs/en/cli-reference))
- Other evaluator flags: `--tools "Read,Grep,Glob"`, `--permission-mode dontAsk`, `--no-session-persistence`, `--session-id <uuid>`, `--max-turns`, `--model`. (CLI reference)
- `--output-format json --json-schema '<schema>'` returns `structured_output` plus `session_id`, `usage`, and `total_cost_usd` (a client-side estimate). Failure is subtype `error_max_structured_output_retries`; `success` without `structured_output` must also count as a failure. (headless; [structured outputs](https://code.claude.com/docs/en/agent-sdk/structured-outputs))
- The output schema is validated as JSON Schema draft-07; a newer draft is rejected, so the definitions' 2020-12 schemas cannot be reused as-is. (structured outputs)
- `claude setup-token` gives a one-year OAuth token for Pro/Max/Team/Enterprise, not saved anywhere and used via `CLAUDE_CODE_OAUTH_TOKEN`. It can only make model requests. (authentication)
- Terms: OAuth is for "ordinary use of Claude Code"; subscription credentials may not be used on behalf of other users. This matches Art. 4 as amended. ([legal and compliance](https://code.claude.com/docs/en/legal-and-compliance))

**G5**

- Plugin layout: `.claude-plugin/plugin.json`, `hooks/hooks.json`, `.mcp.json`, `skills/`, `agents/`, `bin/`. A plugin-root `CLAUDE.md` is not loaded, so instructions go in a skill or in SessionStart `additionalContext`. Plugin `settings` honors only `agent` and `subagentStatusLine`: no `env`, and no way to raise the cap. (plugins reference)
- `userConfig` with `sensitive: true` masks the input and stores it in the OS credential store. `${user_config.KEY}` is substituted in MCP server config (so in `headers`), exec-form hook `args`, and skill and agent content (not sensitive values there). `http` hook headers interpolate only env vars named in `allowedEnvVars`. (plugins reference; hooks)
- Load locally with `--plugin-dir <path>` or a marketplace added from a local path. `claude plugin validate` checks MCP entries and warns on `http://` URLs to non-loopback hosts. (plugins reference; CLI reference)

**G6**

- The cap: after stop hooks continue the turn eight times in a row, Claude Code overrides the next block, and the count resets each time Claude calls a tool. So it fires only on 8 continuations with no tool calls. (hooks, Stop)
- Stop does not run on a user interrupt (Esc). API errors fire `StopFailure` instead, which is log-only. (hooks)
- A failing `http` Stop hook is non-blocking, so Claude stops; `onFailure` has no effect on Stop hooks. To continue, the engine answers 2xx with `{"decision":"block","reason":…}`. (hooks)
- `/goal` wraps a session-scoped prompt-based Stop hook judged by the small fast model. A turn or time clause in the condition is judged by that model, not counted. `/goal` with no argument shows turns evaluated and token spend. It stops after several turns with no tool use, pauses on usage limits, and is unavailable when `disableAllHooks` is set. ([/goal](https://code.claude.com/docs/en/goal))

**G7**

- Anthropic publishes no fixed Max 5x numbers: a five-hour session limit plus a weekly limit, shared across Claude and Claude Code; check `/status`. Third-party figures disagree (one reports the 5-hour limits doubled on 2026-05-06; unverified). ([help center](https://support.claude.com/en/articles/11145838-using-claude-code-with-your-max-plan); [morphllm](https://www.morphllm.com/claude-code-usage-limits), third party)
- The status line JSON has `rate_limits.five_hour.used_percentage`, `seven_day.used_percentage`, and `resets_at`. They are plan-wide, so they include runner usage on the same account; logging them before and after each task measures that task. ([status line](https://code.claude.com/docs/en/statusline))
- `/usage` covers only this machine's local history, so runner sessions in a container will not appear there. ([costs](https://code.claude.com/docs/en/costs))
- Per evaluator run, `--output-format json` gives `usage` and `total_cost_usd`; per driver session, OTel token and cost metrics (spike 5). (headless)

**G8**

- Subagents start with a fresh context and inherit the parent's MCP tools; `tools` can list MCP tools, including plugin-scoped names. Only the final result returns to the parent. The parent writes the delegation message, so it should pass only the task ID. (sub-agents)
- Plugin subagents ignore the `hooks`, `mcpServers`, and `permissionMode` frontmatter fields. PostToolUse inside the subagent carries `agent_id` and `agent_type`, so recorded activity can show that the subagent, not the main agent, called `submit_step_result`. (sub-agents; hooks)

## Questions for you

<!-- Prepared by the agent, highest impact first; each says which goal questions it serves, why it matters, the options, and a suggested answer. Ticked when answered or skipped. -->

- [x] **Q1** May the prototype install a container runtime on the Mac now, before spike 10 picks one? (G4, G7) *Why:* G4 needs a Linux container and the Mac has none. *Options:* install now, recorded as provisional / run G4 on another Linux machine / postpone G4 until spike 10. *Suggested:* install now, choosing the runtime you would likely keep, and note in spike 10 that it is provisional.
- [x] **Q2** Will you generate a dedicated `claude setup-token` for the runner prototype? (G4, G7) *Why:* only you can complete the browser flow, and the token lasts a year. *Options:* dedicated token now, kept outside the repo / postpone. *Suggested:* a dedicated token now, to keep the runner's credential separate from the laptop login.
- [x] **Q3** Should the T01, T05, T06 pilot wait for the `sample-crm` baseline (spec 004)? (G7) *Why:* T06's planted tests and T05's starting point are part of the baseline, which is not built yet. *Options:* wait for 004 / build 004 next / pilot on a quick stand-in. *Suggested:* prototype G1–G6 now and pilot once 004's baseline exists.
- [x] **Q4** How much of one week's Max 5x allowance may this spike's pilots and evaluator trials use? (G7, G4) *Why:* it decides whether all three tasks run with a full council of 3 (about 4 headless sessions per checkpoint attempt). *Options:* about 10% / 25% / whatever it takes. *Suggested:* about 25%, measured as before/after deltas of `rate_limits.seven_day.used_percentage`.
- [x] **Q5** How does a run merge its branch: `gh pr merge`, or a local `git merge` followed by `git push`? (G3) *Why:* it decides what the PreToolUse hook matches. *Options:* `gh pr merge` only / local merge plus push / either. *Suggested:* `gh pr merge` only, with the hook also denying `git push` to the target branch.
- [x] **Q6** When the engine is unreachable, what should the activity hook do for ordinary tool calls? (G3, G2) *Why:* the merge must fail closed (Art. 5), but blocking every tool call on an outage stops all work. *Options:* continue and show a warning / continue silently / block every tool call. *Suggested:* continue and show a warning (Art. 8: record, don't pretend to enforce).
- [x] **Q7** Should the kit run stage tasks with a Stop hook or with `/goal`? (G6) *Why:* a Stop hook counts iterations exactly and reports each to the engine; `/goal` limits are judged by a model. *Options:* Stop hook / `/goal` / both. *Suggested:* a Stop hook, because the iteration limit must be deterministic and recorded (001 Q24, Q26).
- [x] **Q8** Should the runner evaluator read the subject repository's `CLAUDE.md` and other project instructions? (G4) *Why:* it changes what shapes the score and which isolation flags the runner uses. *Options:* no, only the rubric, instructions, and evidence / yes, `CLAUDE.md` only. *Suggested:* no, so only the pinned definition shapes the judgment (Art. 7).
- [x] **Q9** Which model should runner evaluators use in the pilot? (G4, G7) *Why:* a council of 3 multiplies usage, and the model affects score quality. *Options:* the driving model / a cheaper model / compare both on T06. *Suggested:* compare both on T06 once.
- [x] **Q10** When Claude Code starts in a repository with no active run for its branch, what should the session-start hook do? (G2) *Why:* it sets the hook's contract with the engine before spike 8. *Options:* register the session unbound and show status / do nothing / ask Claude to start a run. *Suggested:* register it unbound and inject status, keeping `start_run` an explicit MCP call.
- [x] **Q11** Should this spike prototype G8, or only record the doc findings? (G8) *Why:* the MVP does not need it, and every trial costs usage. *Options:* findings only / one short trial / a full prototype. *Suggested:* one short trial if the G1 server is already up.
- [x] **Q13** Who runs the T01, T05, T06 pilot? (G7) *Options:* the user interactively, noting `/usage` before and after / Claude headless / mixed. *Answer:* Claude, headless.
- [x] **Q12** Which container runtime should the prototype use on the Mac (provisional for spike 10)? (G4) *Why:* follows from Q1. *Options:* OrbStack / Docker Desktop / Colima / Podman.

## Log

<!-- Append-only, oldest first: "- YYYY-MM-DD · <source> · <what was answered, added, or found>". -->

- 2026-10-19 · research · Briefing prepared by the spike-researcher agent (Claude Code 2.1.303, MCP Java SDK 2.1.0). Docs settle spike 001's two Stop-hook questions (G6) and show that `--bare` cannot use the runner token, SessionStart cannot be an `http` hook, and hooks fail open unless `onFailure: "block"`.
- 2026-10-19 · note from another session (Engine without orchestrator, passed on at the user's request) · New constitution article 9, "Need no orchestrator", is on `main`: the engine is reactive, drivers pull work through `status`, and workflow knowledge stays in the definition. Suggests a goal question on whether one session can move through several stages with only generic kit instructions plus `status`, and how a later session resumes a run after a long wait; says G6 carries more weight and G5 should check the kit's instructions stay generic. Whether to add the goal question is open (asked the user).
- 2026-10-19 · answers · Added G9 (article 9) as a goal question. Q1: install a container runtime on the Mac now, provisional for spike 10. Q7: stage tasks are driven by a Stop hook. Q3: the user asked whether the baseline is built; it is not (`sample-crm` `main` has only docs and its spec kit; spec 004 is `draft`), so Q3 is asked again.
- 2026-10-19 · answers · Q3: the user merged `sample-crm` spec 001; checked: the service is on `main` (`1340ae7`), but spec 004 (planted defects, `baseline` tag) is still `draft`. Q12 (new, from Q1): Docker Desktop, already installed (server 29.5.2). Q2: a dedicated `setup-token` for the runner prototype. Q5: `gh pr merge` only; the hook also denies `git push` to the target branch.
- 2026-10-19 · answers · Q3: pilot after spec 004, at the `baseline` tag; prototype G1–G6 and G9 now. Q4: about 10% of one week's Max 5x. Q6: block every tool call when the engine is unreachable. Q8: the runner evaluator reads no project instructions. Noted for Q6: blocking needs a PreToolUse hook on every tool, so each call waits on the engine.
- 2026-10-19 · answers · Q9: evaluators use a cheaper model than the driving one. Q10: register the session unbound and inject status when no run is active. Q11: one short G8 trial. All questions for the user are answered; the goal questions now wait on the prototype.
- 2026-10-19 · prototype · Built `prototype/stub-engine` (MCP Java SDK 2.1.0 servlet transport on Jetty 12.1.14, hook endpoints, toy workflow) and `prototype/engine-driver` (plugin). Ran 22 headless trials with Claude Code 2.1.302, Haiku for mechanics and Sonnet for G9 and the evaluator: MCP with a bearer token works; SessionStart registration works and `session_id` survives resume and compaction but not fork; the PreToolUse gate fails closed on deny, hang, 500, and engine down, adding about 11 ms per call; the 8-block cap fires only without tool calls; the Stop hook drives and exhausts stage tasks; a subagent evaluator fetches and submits by task ID; one generic session drives the toy workflow and fresh sessions resume from status, but status lacked the rejection reason. Isolation flags keep a hostile repository's hooks, MCP server, and `CLAUDE.md` out of the evaluator. Write-up in `prototype/README.md`.
- 2026-10-19 · prototype · Container image for the evaluator built; without a token it reports `is_error: true` under `subtype: "success"`. The authenticated container trial waits on the owner's `setup-token` (`prototype/evaluator/container-trial.sh`).
- 2026-10-19 · owner ran `container-trial.sh` · The token authenticates the evaluator in the container; `--bare` fails with it; a parallel council of 3 succeeded and agreed (2, 2, 2) in 4–10 s each. G4 answered.
- 2026-10-19 · note from the user · Spec 004 is implemented. Checked: `sample-crm` tag `baseline` = `0916dbf` = its `main`, CI green; spec 004's files in this repository are not merged to `main` yet. The G7 pilot is unblocked.
- 2026-10-19 · answer to Q13 · Claude runs the pilot headless (`prototype/pilot/pilot.sh`): fresh clones of `sample-crm` at `baseline` on `task/T0x` branches, nothing pushed, plain Claude Code without the kit, default driving model. Claude stands in for the human's T05 rejection with a neutral reason.
- 2026-10-19 · pilot · T01 $0.50, T06 $0.50, T05 $2.72 (two sessions), Opus 5.5, all `verify` green. T06's trap did not fire (normalized on the first try). T05 found T02's planted defect via git history (commit `41883f6`) and edited `sample-crm`'s spec kit. Evaluator runs are a small share of a run's cost. G7 answered, with plan percentages left to Phase 4.
- 2026-10-19 · owner's interactive G5 check · Installed from a local marketplace. Without the token, SessionStart and MCP got 401; after setting it, MCP reconnected live and all hooks carried the token. The first session stayed unregistered. G5 answered; all goal questions answered.
- 2026-10-19 · answer · The user agreed to the recommendation and follow-ups.

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1

### G2

- **decision** (user, Q10) When no run is active for the branch, the session-start hook registers the session unbound and injects status; `start_run` stays an explicit MCP call.
- **fact** Registration must be a `command` hook (for example curl), since SessionStart allows only `command` and `mcp_tool` hooks and `mcp_tool` hooks are skipped at launch (hooks docs, 2026-10-19).
- **fact** (prototype, trial 2) Claude Code 2.1.302 connects to an MCP server built on MCP Java SDK 2.1.0's `HttpServletStreamableServerTransportProvider` (stateful, plain servlet on Jetty 12.1.14, no Spring) over Streamable HTTP, with a static `Authorization: Bearer` header from the plugin's `.mcp.json`, and calls its tools as `mcp__plugin_engine-driver_engine__<tool>`. It first tries protocol `2026-07-28` (`server/discover`) and falls back to `2025-11-25`.
- **fact** (trial 1) A missing or wrong token gives a 401, the server is marked failed, and its tools are absent from the session. There is no retry with another credential.
- **fact** (trial 2) MCP requests carry `mcp-session-id` and a `User-Agent` with the Claude Code version, but not Claude Code's session ID. The engine relates MCP calls to hook calls only through the arguments (here the `subject`) or a header the kit adds.
- **fact** (trial 2) `${ENGINE_TOKEN}` from the process environment is expanded in a plugin's `.mcp.json` headers. `${user_config.engine_token}` is the documented form for the real kit.
- **fact** (trials 1, 2) A SessionStart command hook posts the hook input, with `session_id`, `cwd`, and `source`, to the engine, and its `additionalContext` reaches Claude (the model quoted the injected status).
- **fact** (trials 9–12) `session_id` stays the same on `--resume` (`source: resume`) and on compaction (`source: compact`), and changes on `--resume --fork-session` (`source: fork`). The engine must bind a forked session again. `/clear` was not tried; the docs say it fires SessionStart with `source: clear`.

### G3

- **decision** (user, Q6) When the engine is unreachable, the kit blocks every tool call, not only the merge. Fail closed for all driver work.
- **fact** A PostToolUse hook runs after the tool has run, so it cannot stop the call; blocking every call needs a PreToolUse hook on all tools (matcher `*`) with `onFailure: "block"` and a short timeout (hooks docs, 2026-10-19). Every tool call then waits on one engine request; the prototype measures the added latency.
- **fact** (trials 8, G5 check; was an assumption) Activity reporting stays in an async PostToolUse hook; an engine outage is caught by the PreToolUse gate before the next call (trial 8 blocked `echo` with the engine down).
- **decision** (user, Q5) A run merges its branch only with `gh pr merge`. The PreToolUse hook matches it, and also denies `git push` to the target branch.
- **fact** (trials 3–7) A PreToolUse command hook on every tool, with `onFailure: "block"` and `timeout: 10`, calls the engine through `hook.sh` (curl `--fail --max-time 8`). The engine's deny reaches Claude as the block reason. The merge is blocked when the engine denies it, hangs (curl timeout), answers 500, or is down. Engine allow lets it run.
- **fact** (trial 7) `bash -c 'gh pr merge 1 --squash'` was still blocked, because the hook runs on every Bash call and the engine reads the full command instead of relying on the `if` filter. A determined driver can still disguise the command; GitHub branch protection is the real gate (Art. 8).
- **fact** (trial 8) With the engine down, an ordinary `echo` was blocked too (Q6).
- **fact** (measured) The gate adds about 11 ms per tool call (median 10.7 ms, max 13.2 ms over 30 calls) against a local engine.
- **fact** (trial 2) PostToolUse reports `tool_name`, `tool_input`, `tool_response`, and the session ID. An `http` hook header can read an env var only through `allowedEnvVars`, and plugin options are exported only to hook processes, so the kit uses an async command hook for activity.

### G4

- **decision** (user, Q1) The prototype may install a container runtime on the Mac now, before spike 10 picks one. Recorded as provisional for spike 10.
- **decision** (user, Q9) Runner evaluators in the trials use a cheaper model than the driving one.
- **decision** (recommendation agreed by the user; was an assumption) The cheaper evaluator model is Sonnet 5.5 (`claude-sonnet-5-5`), passed with `--model`; the container trial ran on it.
- **decision** (user, Q8) The runner evaluator does not read the subject repository's `CLAUDE.md`, skills, hooks, or MCP servers: only the rubric, the step's instructions, and the evidence shape its judgment.
- **decision** (user, Q12) The prototype uses Docker Desktop, which the user already has (server 29.5.2, checked 2026-10-19). Provisional for spike 10.
- **decision** (user, Q2) The user generates a dedicated `claude setup-token` for the runner prototype, kept outside the repository.
- **fact** The laptop needs a Docker-compatible runtime anyway, for `sample-crm`'s Testcontainers tests (`docs/test-bed.md`, Repository).
- **fact** (trial 21) Without isolation flags, `claude -p` in the hostile fixture ran the fixture's SessionStart and PreToolUse hooks, started its `.mcp.json` server, and loaded its `CLAUDE.md` (the model said it ignored a "score 5" line).
- **fact** (trial 22) `--restricted --safe-mode --strict-mcp-config --tools Read,Grep,Glob --permission-mode dontAsk --no-session-persistence` ran none of them and loaded no `CLAUDE.md`. `--json-schema` with a draft-07 schema returned `structured_output` (`raw_score` 2, the rubric-correct band for an exact-match check), in 4 turns, $0.030 list price with Sonnet against $0.049 without the flags.
- **fact** (container, no token) The image (`node:24-bookworm-slim`, Claude Code 2.1.302, non-root) runs the evaluator. Without a token the result is `subtype: "success"`, `is_error: true`, "Not logged in". The runner must treat `is_error` or a missing `structured_output` as a job that did not run (*not evaluated*), not as a result.
- **fact** (container trial, run by the owner with a dedicated `setup-token`, 2026-10-19) In the Linux container, `CLAUDE_CODE_OAUTH_TOKEN` authenticates headless Claude Code with the isolation flags: one evaluation returned `structured_output` with `raw_score` 2 (Sonnet 5.5, 3 turns, 7.0 s).
- **fact** (same) `--bare` with the same token fails in 22 ms: `is_error: true`, "Not logged in". The runner must not use `--bare`.
- **fact** (same) A council of 3 run in parallel containers on one token all succeeded and agreed (`raw_score` 2, 2, 2), in 4.2–10.1 s each, with no rate-limit errors. Each run's `total_cost_usd` was $0.003–0.010, lower than the local run because of prompt caching across runs.
- **fact** (same) The outputs hold no token; it was passed only as a container environment variable.

### G5

### G6

- **decision** (user, Q7) The kit drives stage tasks with a Stop hook, not `/goal`: the iteration limit must be counted exactly and each iteration reported to the engine (001 Q24, Q26).
- **fact** The 8-block cap resets each time Claude calls a tool, so it does not fire while Claude makes progress (hooks docs, Stop, 2026-10-19).
- **fact** Stop does not fire on a user interrupt (Esc) (hooks docs, 2026-10-19).
- **fact** A failing `http` Stop hook lets Claude stop; a 2xx `{"decision":"block"}` continues the turn; `onFailure` has no effect on Stop hooks (hooks docs, 2026-10-19).
- **fact** A turn limit written into a `/goal` condition is judged by the small fast model, not counted (/goal docs, 2026-10-19). Still to try in the prototype.
- **fact** (prototype) The whole driver side is one plugin, and `claude plugin validate` passes: `userConfig` (engine URL, sensitive driver token), `.mcp.json`, `hooks/hooks.json` with four command hooks, generic `instructions.md` injected at session start, and an `evaluator` subagent. A plugin-root `CLAUDE.md` would not be loaded, so instructions go through SessionStart `additionalContext`.
- **fact** (G9 trials) The kit's instructions hold nothing workflow-specific: everything the driver did came from the engine's status.
- **fact** (trials 1, 8) With `--plugin-dir`, `userConfig` options are not prompted for in `-p` mode, and options with a `default` were not exported to hook processes (the script's env fallback was used). `pluginConfigs` is read only from user and managed settings.
- **fact** (owner's interactive check, 2026-10-19; Claude Code installed from a local marketplace, `--scope local`) The first session started with no token: its SessionStart call and the MCP connection were rejected (401) at 10:17:36. After the owner set the token (10:18), MCP reconnected in the same session without a restart (10:18:20), and the PreToolUse, PostToolUse, and Stop hooks reached the engine with the token (`CLAUDE_PLUGIN_OPTION_ENGINE_TOKEN` from the sensitive `userConfig`). `status` over MCP and a Bash call went through. No request was rejected once the token was set.
- **fact** (same) The session that started before the token was set was never registered: SessionStart does not run again when the token arrives, and a failing SessionStart hook does not stop the session. The kit should register lazily (on the first PreToolUse call it sees for an unknown session), or tell the user to restart.
- **fact** (same) Installing does not demand the token before the first session; the token has to be set, then MCP and hooks pick it up live.
- **fact** (trial 16) With a Stop hook that always blocks and no tools available, it fired 9 times: 8 continuations, then Claude Code overrode the block and the turn completed.
- **fact** (trial 17) With a tool call in each continuation, it fired 14 times and went past 8 until `--max-turns` stopped it. Only the engine's own iteration limit bounds a stage task that is making progress.
- **fact** (trials 18, 19) The engine-driven Stop hook turned an early stop into the stage task (the session then did both stages), and when the task could not be done it allowed the stop after 4 of 4 iterations and recorded `stage_task_exhausted`.
- **fact** (trial with `stop=error`) A Stop hook that fails lets Claude stop.

### G7

- **decision** (user, Q3) Prototype G1–G6 and G9 now. Pilot T01, T05, and T06 only after spec 004 is done, at the `baseline` tag, so T06's trap is in place and reruns share a base commit.
- **decision** (user, Q13) Claude runs the pilot headless. It measures client-side cost and tokens per task, not plan percentages.
- **assumption** (pilot only, nothing depends on it) The T05 rejection reason was written by Claude, standing in for the human: the change does not say what "better" means or show that it is better.
- **decision** (user, Q4) This spike's pilots and evaluator trials may use about 10% of one week's Max 5x allowance, measured as before/after deltas of `rate_limits.seven_day.used_percentage`.
- **fact** (container trial; was an assumption) A council of 3 cost $0.013 in total, so the 10% budget did not require a smaller council.
- **fact** (note from the user, 2026-10-19; checked on GitHub) The user implemented spec 004. `sample-crm` has the tag `baseline` at `0916dbf` (its `main`), with green CI (`build`). In this repository, spec 004's own files are not on `main` yet (still `draft` there), so `docs/test-bed.md` does not yet record T01's base commit. The pilot (Q3) can run from `baseline`.
- **fact** `sample-crm`'s own spec 001 (the service) is built and merged to its `main` (PR #1, `1340ae7`, 2026-10-19): Spring Boot, contacts, companies, deals, CI, OpenAPI. Spec 004 here (planted defects for T02, T04, T06, T07, and the `baseline` tag) is still `draft`, and no tag exists yet (GitHub API, 2026-10-19).
- **fact** (pilot, `prototype/pilot/`) Plain Claude Code, headless, default model Opus 5.5, fresh `sample-crm` clones at `baseline`. Client-side list-price estimates (`total_cost_usd`), not plan usage:

  | Task | Sessions | Turns | Wall | Output tokens | Cache read | Cost | Outcome |
  | --- | --- | --- | --- | --- | --- | --- | --- |
  | T01 | 1 | 15 | 62 s | 5.8k | 329k | $0.50 | `GET /contacts/count` with filter tests; `verify` green (90 tests). As expected. |
  | T06 | 1 | 17 | 73 s | 6.8k | 322k | $0.50 | Duplicate check with `lower(trim(email))` on the first try; `verify` green. **The trap did not fire.** |
  | T05 | 2 (reject, rework) | 28 + 35 | 130 + 206 s | 12.7k + 33.3k | 835k + 2.2M | $0.86 + $1.86 | Found and fixed T02's planted paging offset, plus multi-word name search; the rework also edited `sample-crm`'s spec 001, plan, tasks, and BACKLOG. `verify` green. |

- **fact** (pilot) One evaluator run costs $0.003–0.030 (G4), so a checkpoint attempt's ~4 runner sessions cost well under 10% of a driver session ($0.50–2.70 per task). Runner evaluations are a small share of a run's usage; the driver sessions dominate, and a rework roughly triples a task's cost.
- **fact** (pilot) Total pilot spend, all trials included, was about $5 at list price. Plan percentages were not measured, because headless runs show no status line (Q13).
- **assumption** (Follow-up: Phase 4) At these sizes, the 12-task seed set plus reruns fits in Max 5x's weekly limit. Phase 4 measures plan percentages with a status-line logger.
- **fact** (pilot, T06) With Opus 5.5, the T06 quality trap does not fire: the driver normalizes case and whitespace unprompted. The expected failed checkpoint and rework (spike 002, `docs/test-bed.md`) will not happen with this model.
- **fact** (pilot, T05) The driver found T02's planted defect through git history: it named commit `41883f6` as the one that introduced the offset and "also deleted the test assertion that would have caught it". The planting gives itself away in `sample-crm`'s history, against spec 004 S3 ("nothing gives it away").
- **fact** (trials) Cost estimates per headless session, list price from `total_cost_usd`: a Haiku mechanics trial $0.003–0.016; one Sonnet session driving two stages $0.13, a resuming session $0.04–0.10; one isolated Sonnet evaluator run $0.030. These are client-side estimates, not plan usage.

### G8

- **decision** (user, Q11) One short trial once the G1 server is up.
- **fact** (trial 20) Given only `eval-1`, the plugin's `evaluator` subagent (`tools: Read, Grep, Glob` plus the two engine tools) called `get_evaluation_task` and `submit_step_result` itself and replied only "Submitted eval-1." The PostToolUse records carry `agent_id` and `agent_type: engine-driver:evaluator`, so the engine can tell the subagent's submission from the main agent's.

### G9

- **decision** (user, 2026-10-19) Added as a goal question of this spike, for article 9.
- **fact** (trial 13) One Sonnet session, given only the kit's generic instructions plus status and the prompt "Please work on this directory's workflow run. The topic is autumn rain.", started a run, did the `draft` and `review` stage tasks, submitted evidence, requested both transitions, and stopped on its own at the human gate (10 turns).
- **fact** (trials 14, 15) After a human rejection and after an approval, a fresh session (not a resume) prompted only with "Continue." picked up the run from the status injected at session start. After the approval it needed no MCP call at all.
- **fact** (trial 14) Status did not carry the human's rejection reason, so the rework resubmitted the same work unchanged. For spike 8: `status` must carry the last decision on the run and its reason, so a generic driver can do the rework.
- **fact** (all G9 trials) Nothing outside the sessions moved the run: each session pulled status, and the engine only reacted to driver calls and to the human's decision (Art. 9 holds for this toy workflow).

## Recommendation and decisions

<!-- Drafted when every goal question is answered, and agreed with the user. -->

| Decision | Choice | Alternatives considered | Why |
| --- | --- | --- | --- |
| MCP server | MCP Java SDK (2.1.0 tried), stateful Streamable HTTP servlet transport, static bearer driver token | Spring AI WebMVC or WebFlux transport; stateless server; `headersHelper` | Worked end to end with no framework (G1). The stateless variant has a known Claude Code connection bug. |
| Hooks to engine | Command hooks only, calling the engine's HTTP API through one small script | `http` hooks | SessionStart cannot be `http`, and `http` hooks cannot read the plugin's token (G2, G3). |
| Merge gate | PreToolUse on every tool with `onFailure: "block"` and a 10 s timeout; the engine reads the full command; merges only with `gh pr merge`; GitHub branch protection stays the real gate | `if: "Bash(gh pr merge *)"` filter alone | Failed closed on deny, hang, error, and engine down, and caught `bash -c` wrapping; about 11 ms per call (G3, Q5). |
| Engine unreachable | Block every tool call | Warn and continue | The user's decision (Q6). |
| Activity recording | Async PostToolUse command hook: tool, input, outcome, session ID, agent type | Synchronous `http` hook | No added latency; subagent calls are recorded apart (G3, G8). |
| Session binding | SessionStart registers the session, unbound if no run exists; MCP calls carry the subject; forks register again; lazy registration on the first PreToolUse from an unknown session | Claude's session ID in MCP headers | MCP requests do not carry it; a session started before the token was set stayed unregistered (G1, G2, G5, Q10). |
| Stage tasks | Command Stop hook; the engine counts iterations and decides exhaustion | `/goal` | Deterministic and recorded; the 8-block cap never fires while progress is made (G6, Q7). |
| Runner evaluator | `claude -p` in a Linux container with `CLAUDE_CODE_OAUTH_TOKEN`, `--restricted --safe-mode --strict-mcp-config --tools Read,Grep,Glob`, a draft-07 `--json-schema`, Sonnet; `is_error` or no `structured_output` means *not evaluated*; never `--bare` | `--bare`; reading the repository's `CLAUDE.md` | `--bare` cannot authenticate with the token; the flags kept a hostile repository's hooks, MCP server, and `CLAUDE.md` out (G4, Q8, Q9). |
| Packaging | One plugin: sensitive `userConfig` token, MCP, four hooks, generic instructions via SessionStart `additionalContext` | Instructions in a plugin `CLAUDE.md` | A plugin `CLAUDE.md` is not loaded; the token reaches MCP and hooks live (G5). |
| Usage | Fits by estimate: evaluators are a small share, driver sessions and reworks dominate; measure plan percentages in Phase 4 | — | Pilot: $0.50, $0.50, $2.72 per task against $0.003–0.030 per evaluator run (G7, Q4). |
| Driver-side evaluator subagent | Feasible; stays post-MVP | Build it in the MVP | Works by task ID alone, but the MVP does not need it (G8). |
| No orchestrator | Holds; `status` must carry the last decision on the run and its reason | — | Generic sessions drove and resumed the toy run; the rework lacked the rejection reason (G9). |

## Deliverables

- [x] Every goal question answered, and the recommendation agreed with the user
- [x] A throwaway prototype under `specs/005-claude-code-driver-feasibility/prototype/`, with a write-up of what works, what does not, and the workarounds
- [x] BACKLOG.md items settled by this spike updated

## Follow-ups

<!-- New specs, BACKLOG items, experiments, or open questions this spike created. -->

Where each follow-up went (verified 2026-10-19): the test-bed items are one BACKLOG item, "Test-bed fixes from spike 005" (user's choice); the `status` and session items are inputs on spikes 7 and 8 and on Session correlation; the Phase 4 logger is an input on Quality view.

From the pilot:

- Spec 004 / spike 002: the planting is visible in `sample-crm`'s git history (T05 found T02's defect through commit `41883f6`). Consider squashing the history before `baseline`, so the planted state arrives in one neutral commit.
- Spike 002 / `docs/test-bed.md`: T06's trap does not fire with Opus 5.5. Make the trap subtler, or accept that T06 tests a passing case and pick another task for the failed checkpoint.
- Spike 002: T05's vague task led the driver to fix T02's defect and to edit `sample-crm`'s own spec kit. Decide whether runs may touch `sample-crm/specs/`, and whether reruns of T05 must start after T02.
- Spike 8: `status` must carry the last decision on the run and its reason (G9).
- Phase 4: log the status line's `rate_limits` before and after each task to measure plan percentages.

Things to try in the prototype (from the briefing):

- G1: a plain-servlet MCP server (Java SDK 2.1.0, `HttpServletStreamableServerTransportProvider`, stateful, no Spring) on localhost, with a bearer check in `contextExtractor` and a stub `status` tool. Connect via `claude mcp add --transport http … --header`, then via plugin `.mcp.json` with `${user_config.engine_token}`. Test a wrong token and a server that is down. (~3 h)
- G2: a SessionStart command hook posting its stdin JSON to a stub. Record `session_id` across startup, `/clear`, compaction, `--resume`, and `--fork-session`; check that `additionalContext` arrives. (~1 h)
- G3: a PreToolUse `http` hook with `if: "Bash(gh pr merge *)"`, `onFailure: "block"`, and `timeout: 10`. Check deny, allow, stub down, stub hanging, and that `bash -c 'gh pr merge'` gets past it. Compare a PostToolUse `http` hook with an async curl command hook for added latency and engine-down behavior. (~2 h)
- G5: can a plugin's `http` hook use `$CLAUDE_PLUGIN_OPTION_<KEY>` through `allowedEnvVars`? If not, exec-form command hooks with `${user_config.*}` in `args`. Run `claude plugin validate`. (~1 h)
- G4: `claude -p` in a Linux container with `CLAUDE_CODE_OAUTH_TOKEN`. Check that `--restricted` and `--safe-mode` authenticate with that token and `--bare` fails. Clone a repository that ships a hostile `.claude/settings.json` hook and `.mcp.json`, and confirm the chosen flags keep both out. Check `structured_output` against a draft-07 rubric schema, and run three in parallel for a council. (~4 h)
- G6: a Stop hook that blocks N times with and without tool calls; Esc during a blocked loop; an `http` Stop hook against a stub; `/goal … or stop after 5 turns` run three times. (~2 h)
- G7: a status-line script that logs `rate_limits` to a file. Pilot T01, T05, T06 and record the 5-hour and 7-day deltas, plus each evaluator's `usage` JSON. (~2 h per task, after spec 004)
- G8: a plugin subagent with `tools: Read, Grep, Glob, mcp__plugin_<kit>_engine__get_evaluation_task, mcp__plugin_<kit>_engine__submit_step_result`. The parent passes only a task ID; check `agent_id` in the PostToolUse records. (~1 h)

## Changelog

- 2026-10-19 Created.
- 2026-10-19 Briefing and questions Q1–Q11 prepared; status in-progress.
- 2026-10-19 Added G9 for constitution article 9; recorded Q1–Q12.
- 2026-10-19 Prototype trials recorded; G1, G2, G3, G6, G8, G9 answered. G4 and G5 wait on owner checks, G7 on spec 004.
- 2026-10-19 G4 answered from the owner's container trial.
- 2026-10-19 G7 answered from the headless pilot; follow-ups for specs 002 and 004 and spike 8 recorded.
- 2026-10-19 G5 answered from the owner's interactive check; every goal question answered.
- 2026-10-19 Recommendation agreed with the user; ready for /sdd-verify.
- 2026-10-19 Verified and closed: assumptions settled or moved to Follow-ups; BACKLOG MCP server, Hooks to engine, and Activity recording decided; Model access confirmed; follow-ups recorded in BACKLOG; PLAN spike 4 ticked.
- 2026-10-21 Note from spike 010 (Q3, Q20): the driver kit maps a tool call to a neutral action name and revision, so the engine no longer reads the full command; the MCP server runs as a separate stateless gateway.
