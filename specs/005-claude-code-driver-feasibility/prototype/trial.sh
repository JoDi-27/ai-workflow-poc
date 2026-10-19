#!/bin/sh
# Runs one headless driver trial against the stub engine and prints the result and the engine events.
# Usage: trial.sh <workdir> <model> <max-turns> <prompt> [extra claude args...]
P="$(cd "$(dirname "$0")" && pwd)"
dir="$1"; model="$2"; turns="$3"; prompt="$4"; shift 4
: > "$P/stub-engine/events.jsonl"
mkdir -p "$dir" && cd "$dir" || exit 1
ENGINE_TOKEN=dev-token claude -p --plugin-dir "${KIT:-$P/engine-driver}" --model "$model" --output-format json \
  --max-turns "$turns" "$@" > "$P/last-result.json" 2>&1 <<PROMPT
$prompt
PROMPT
jq -r '"result: " + (.result // "?") + "\nturns: \(.num_turns) denials: \(.permission_denials|length) cost: \(.total_cost_usd)"' "$P/last-result.json" 2>/dev/null || tail -c 600 "$P/last-result.json"
jq -c 'select(.kind!="mcp_http") | {kind, tool: (.tool // .input.tool_name), cmd: .input.tool_input.command, stage, iteration, to, why}
  | with_entries(select(.value != null))' "$P/stub-engine/events.jsonl"
