#!/usr/bin/env bash
# Throwaway capture for spike 006: runs short headless Claude Code sessions with
# telemetry sent to receiver.py on 127.0.0.1:4318. Telemetry env is set only on
# these processes; no settings file of the user is changed.
# Usage: capture.sh <work-dir>   (receiver.py must already write to <work-dir>/capture)
set -euo pipefail

WORK="$1"
PROJ="$WORK/project"
HOOKS="$WORK/hooks.jsonl"
mkdir -p "$PROJ/.claude/skills/word-count" "$PROJ/.claude/agents"

# A tiny project with one skill and one subagent, so their names can be looked for.
cat > "$PROJ/.claude/skills/word-count/SKILL.md" <<'EOF'
---
name: word-count
description: Count the words in notes.txt. Use when asked to count words.
---
Run `wc -w notes.txt` with Bash and report the number.
EOF
cat > "$PROJ/.claude/agents/line-counter.md" <<'EOF'
---
name: line-counter
description: Counts lines in a file. Use when asked to count lines.
tools: Bash
model: haiku
---
Run `wc -l` on the file you are given with Bash and reply with the number only.
EOF
printf 'alpha beta gamma\ndelta epsilon\n' > "$PROJ/notes.txt"

# Hooks log their stdin plus the env session ID, to compare with the OTel session.id.
log_hook='{ printf "{\"hook_env_session_id\":\"%s\",\"input\":" "$CLAUDE_CODE_SESSION_ID"; cat; printf "}\n"; } >> '"$HOOKS"
SETTINGS=$(python3 -c '
import json, sys
h = [{"hooks": [{"type": "command", "command": sys.argv[1]}]}]
print(json.dumps({"hooks": {e: h for e in ["SessionStart", "PostToolUse", "PostToolUseFailure", "Stop"]}}))
' "$log_hook")

base_env=(
  CLAUDE_CODE_ENABLE_TELEMETRY=1
  OTEL_METRICS_EXPORTER=otlp
  OTEL_LOGS_EXPORTER=otlp
  OTEL_TRACES_EXPORTER=otlp
  CLAUDE_CODE_ENHANCED_TELEMETRY_BETA=1
  OTEL_EXPORTER_OTLP_PROTOCOL=http/json
  OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:4318
  OTEL_EXPORTER_OTLP_HEADERS="Authorization=Bearer spike-006-test-token"
  OTEL_METRIC_EXPORT_INTERVAL=5000
  OTEL_LOGS_EXPORT_INTERVAL=1000
  OTEL_RESOURCE_ATTRIBUTES="run.id=spike006-run"
)
details_env=(
  OTEL_LOG_TOOL_DETAILS=1
  OTEL_LOG_USER_PROMPTS=1
  OTEL_LOG_ASSISTANT_RESPONSES=1
)

run() { # run <name> <session-args...> -- <prompt>
  local name="$1"; shift
  local args=()
  while [ "$1" != "--" ]; do args+=("$1"); shift; done; shift
  echo "== $name"
  # A clean env, as in a plain terminal: only the telemetry vars this scenario exported.
  local tel=()
  while IFS= read -r v; do tel+=("$v"); done < <(env | grep -E '^(OTEL_|CLAUDE_CODE_ENABLE_TELEMETRY=|CLAUDE_CODE_ENHANCED_TELEMETRY_BETA=)' || true)
  (cd "$PROJ" && env -i HOME="$HOME" USER="$USER" PATH="$PATH" SHELL="$SHELL" TMPDIR="$TMPDIR" LANG="${LANG:-en_US.UTF-8}" \
    ${tel[@]+"${tel[@]}"} claude -p "$1" "${args[@]}" --model haiku --output-format json \
    --settings "$SETTINGS" --allowedTools "Bash Read Skill Agent Task" \
    > "$WORK/$name.result.json" 2> "$WORK/$name.stderr") || echo "   exit $?"
}

uid() { uuidgen | tr 'A-Z' 'a-z'; }
S1=$(uid); S3=$(uid); S4=$(uid); S5=$(uid)
echo "S1=$S1 S3=$S3 S4=$S4 S5=$S5" > "$WORK/sessions.txt"
TASK='Do these in order: 1) run the Bash command ls /nonexistent-spike-006; 2) use the line-counter subagent on notes.txt; 3) use the word-count skill. Then reply with one short line.'

# S1: details off. A failing Bash, a subagent, and a skill.
( export "${base_env[@]}"; run s1-details-off --session-id "$S1" -- "$TASK" )
# S2: resume S1, to see whether session.id is kept.
( export "${base_env[@]}"; run s2-resume --resume "$S1" -- 'Reply with the word done.' )
# S3: tool details, prompts, and responses on; same tasks.
( export "${base_env[@]}" "${details_env[@]}"; run s3-details-on --session-id "$S3" -- "$TASK" )
# S4: no telemetry env on the process; project settings try to enable it (expected to be ignored).
python3 -c 'import json, sys; print(json.dumps({"env": dict(a.split("=", 1) for a in sys.argv[1:])}))' \
  "${base_env[@]}" > "$PROJ/.claude/settings.json"
run s4-project-settings --session-id "$S4" -- 'Reply with the word four.'
rm "$PROJ/.claude/settings.json"
# S5: no telemetry env on the process; --settings inline env tries to enable it.
INLINE=$(python3 -c '
import json, sys
s = json.loads(sys.argv[1]); s["env"] = dict(a.split("=", 1) for a in sys.argv[2:])
print(json.dumps(s))
' "$SETTINGS" "${base_env[@]}")
SETTINGS="$INLINE" run s5-inline-settings --session-id "$S5" -- 'Reply with the word five.'
echo "done"
