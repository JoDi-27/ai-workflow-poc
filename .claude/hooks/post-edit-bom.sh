#!/usr/bin/env bash
# PostToolUse on Edit|Write: regenerate the inventory in .claude/README.md when a skill, agent, hook, or settings.json changed.
input=$(cat)
file=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')
case "$file" in
  */.claude/skills/*|*/.claude/agents/*|*/.claude/hooks/*|*/.claude/settings.json) ;;
  *) exit 0 ;;
esac
# The checkout that holds the edited file: in a worktree, $CLAUDE_PROJECT_DIR
# is the main checkout, not the worktree.
root=$(git -C "$(dirname "$file")" rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$root" || exit 0
python3 .claude/bom.py >/dev/null 2>&1 || echo ".claude/bom.py failed" >&2
exit 0
