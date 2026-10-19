#!/bin/sh
# Runs one headless evaluator over a checked-out repository and prints the structured result.
# Usage: evaluate.sh <repo-dir> [extra claude args...]   Auth: CLAUDE_CODE_OAUTH_TOKEN, or the local login.
here="$(cd "$(dirname "$0")" && pwd)"
repo="$1"; shift
cd "$repo" || exit 2
claude -p --restricted --safe-mode --strict-mcp-config \
  --tools Read,Grep,Glob --permission-mode dontAsk --no-session-persistence \
  --model "${EVAL_MODEL:-sonnet}" --max-turns 8 \
  --output-format json --json-schema "$(cat "$here/result.schema.json")" "$@" < "$here/prompt.md"
