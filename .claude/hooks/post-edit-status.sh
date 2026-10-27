#!/usr/bin/env bash
# PostToolUse on Edit|Write: regenerate specs/STATUS.md when a spec.md or tasks.md changed.
input=$(cat)
file=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')
case "$file" in
  */specs/[0-9][0-9][0-9]-*/spec.md|*/specs/[0-9][0-9][0-9]-*/tasks.md) ;;
  *) exit 0 ;;
esac
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
specs/_kit/sdd status --write >/dev/null 2>&1 || echo "sdd status --write failed" >&2
exit 0
