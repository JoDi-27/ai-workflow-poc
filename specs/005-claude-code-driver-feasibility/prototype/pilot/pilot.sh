#!/bin/sh
# Spike 005, G7: runs one test-bed task in plain Claude Code (no kit), headless, on a fresh clone of
# sample-crm at the `baseline` tag, and records cost, tokens, turns, and the diff.
# Usage: pilot.sh <task-id> <workroot> <prompt> [--resume-with <follow-up prompt>]
set -u
here="$(cd "$(dirname "$0")" && pwd)"
task="$1"; root="$2"; prompt="$3"; shift 3
out="$here/out/$task"; mkdir -p "$out"
dir="$root/$task"
if [ ! -d "$dir" ]; then
  git clone -q https://github.com/JoDi-27/sample-crm.git "$dir" && git -C "$dir" checkout -q -b "task/$task" baseline
fi
cd "$dir" || exit 1
run() {  # $1 = output name, rest = extra args; prompt on stdin
  name="$1"; shift
  start=$(date +%s)
  claude -p --output-format json --permission-mode acceptEdits \
    --allowedTools "Bash(./mvnw:*)" "Bash(git status:*)" "Bash(git diff:*)" "Bash(git log:*)" "Bash(ls:*)" "Read" "Grep" "Glob" "Edit" "Write" \
    --max-turns 80 "$@" > "$out/$name.json" 2>"$out/$name.err"
  echo "$(( $(date +%s) - start ))" > "$out/$name.wall"
}
printf '%s\n' "$prompt" | run first
if [ "${1:-}" = "--resume-with" ]; then
  sid=$(jq -r .session_id "$out/first.json")
  printf '%s\n' "$2" | run second --resume "$sid"
fi
git add -A >/dev/null 2>&1; git diff --cached baseline > "$out/diff.patch"; git reset -q
for f in "$out"/*.json; do
  jq -c --arg f "$(basename "$f")" --arg wall "$(cat "${f%.json}.wall")" '{file: $f, is_error, subtype, turns: .num_turns, wall_s: ($wall|tonumber),
    cost: .total_cost_usd, models: (.modelUsage|to_entries|map({(.key): {in: .value.inputTokens, out: .value.outputTokens,
    cache_read: .value.cacheReadInputTokens, cache_write: .value.cacheCreationInputTokens}})|add)}' "$f"
done | tee "$out/summary.jsonl"
echo "diff: $(grep -c '^+' "$out/diff.patch") added lines in $(grep -c '^diff --git' "$out/diff.patch") files"
