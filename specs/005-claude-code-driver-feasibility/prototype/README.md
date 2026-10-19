# Spike 005 prototype: Claude Code driver feasibility

Throwaway. It exists to answer the spike's goal questions, not to become the engine or the kit. Findings are recorded in `../spec.md`; this file says what each part is and how to rerun the trials.

Tried on 2026-10-19 with Claude Code 2.1.302, MCP Java SDK 2.1.0, Jetty 12.1.14, Java 25, and Docker Desktop 29.5.2 (arm64).

## Parts

| Path | What it is |
| --- | --- |
| `stub-engine/` | One Java process: the engine's MCP server at `/mcp` (Java SDK, `HttpServletStreamableServerTransportProvider`, stateful, no Spring), the HTTP endpoints hooks call at `/hooks/*`, and `/admin/*` for the experimenter to play the human and switch failure modes. A bearer filter guards `/mcp` and `/hooks`. Every request goes to `events.jsonl`. It runs a toy, non-SDLC workflow: `draft → review → (human) → published`, with a rework edge back to `draft`. |
| `engine-driver/` | The driver kit as a Claude Code plugin: `userConfig` for the engine URL and a sensitive driver token, `.mcp.json` for the engine, `hooks/hooks.json`, `scripts/hook.sh`, generic `instructions.md` (no workflow steps), and an `evaluator` subagent. |
| `trial.sh` | Runs one headless driver session against the stub engine and prints the result and the engine events. |
| `evaluator/` | The runner-side evaluator: `evaluate.sh` with the isolation flags, a draft-07 result schema, a rubric prompt, a hostile fixture repository, a `Dockerfile`, and `container-trial.sh` for the owner to run with their `setup-token`. |

## Rerun

```sh
stub-engine/run.sh                        # terminal 1; ENGINE_TOKEN defaults to dev-token
KIT=<copy of engine-driver whose .mcp.json and hooks read $ENGINE_TOKEN> \
  ./trial.sh <workdir> haiku 5 "Call status and report it."
curl -XPOST 'localhost:8787/admin/mode?merge=hang'    # also: merge=allow|deny|error, pretool=..., stop=engine|block|error, stop_limit=N
curl -XPOST 'localhost:8787/admin/approve?subject=<workdir>'   # or reject
evaluator/evaluate.sh <checked-out repo>               # local login
CLAUDE_CODE_OAUTH_TOKEN=<token> evaluator/container-trial.sh
```

Headless trials used a copy of the plugin that reads the token from `$ENGINE_TOKEN`, because plugin options are set only through the interactive dialog or user settings (`pluginConfigs` is read only from user and managed settings).

## What was shown

- **MCP (G1):** Claude Code connects to the Java SDK servlet transport over Streamable HTTP with a static bearer header and calls its tools as `mcp__plugin_engine-driver_engine__<tool>`. A wrong or missing token gives a 401 and the server is marked failed, with no fallback. MCP requests carry `mcp-session-id` but not Claude Code's session ID.
- **Session start (G2):** a command hook posts the hook input to the engine and returns `additionalContext` (kit instructions plus engine status). `session_id` stays the same on `--resume` and compaction (`source` `resume`, `compact`) and changes on `--fork-session` (`source` `fork`).
- **Merge gate and activity (G3):** a PreToolUse command hook on every tool, with `onFailure: "block"`, denies `gh pr merge` when the engine says so, and blocks it when the engine hangs (curl timeout), answers 500, or is down. `bash -c 'gh pr merge …'` is still caught, because the engine reads the full command. The gate adds about 11 ms per tool call on a local engine. With the engine down, every tool call is blocked (Q6).
- **Stop-hook stage tasks (G6):** the engine's Stop endpoint blocks an early stop with the stage task, counts iterations, and lets the session stop once the limit is exhausted, recording it. The 8-block cap fired after 8 continuations without tool calls, and did not fire while each continuation made a tool call. A Stop hook that errors lets Claude stop.
- **Evaluator (G4, local part):** without isolation flags, the fixture's hooks and MCP server ran and its `CLAUDE.md` was loaded. With `--restricted --safe-mode --strict-mcp-config --tools Read,Grep,Glob`, none of that happened, and `--json-schema` (draft-07) returned `structured_output`. With no token, the result has `subtype: "success"` but `is_error: true`.
- **Subagent evaluator (G8):** given only a task ID, the plugin's subagent fetched the task and submitted its own result through MCP; the hook records carry its `agent_type`.
- **No orchestrator (G9):** one Sonnet session, given only the generic kit instructions and "Please work on this directory's workflow run", started a run, did two stage tasks, and stopped at the human gate. Fresh sessions resumed from status after a rejection and after an approval. Status did not carry the rejection reason, so the rework resubmitted unchanged work.
- **Packaging, interactive (G5):** installed from the local marketplace (`.claude-plugin/marketplace.json`), the kit took the token through `userConfig`; MCP reconnected live once it was set, and the hooks read it from `CLAUDE_PLUGIN_OPTION_ENGINE_TOKEN`. A session started before the token was set stayed unregistered.
- **Container evaluator (G4):** with the owner's `setup-token`, the evaluator ran in the container and a parallel council of 3 agreed; `--bare` failed with the token.
- **Pilot (G7):** `pilot/pilot.sh` ran T01, T06, and T05 headless from `sample-crm`'s `baseline` tag. T06's trap did not fire with Opus 5.5, and T05 found T02's planted defect through git history.
