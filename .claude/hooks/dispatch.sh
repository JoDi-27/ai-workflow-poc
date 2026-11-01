#!/usr/bin/env bash
# Any event: run the named hook script from the session's own checkout, so a worktree session uses its own hooks.
# Usage in settings.json: "$CLAUDE_PROJECT_DIR"/.claude/hooks/dispatch.sh .claude/hooks/<script>
# Claude Code runs hook commands from the main checkout, even for a session in a worktree, so
# without this every session would run the main checkout's copy of each hook. The session's
# directory comes from the hook input's `cwd`. If that checkout has no such script, the main
# checkout's copy runs. Stdin, stdout, stderr, and the exit code pass through unchanged.
input=$(cat)
self_root="$(cd "$(dirname "$0")/../.." && pwd)"
cwd=$(printf '%s' "$input" | jq -r '.cwd // empty' 2>/dev/null)
root=$(git -C "${cwd:-.}" rev-parse --show-toplevel 2>/dev/null) || root=$self_root
script="$root/$1"
[ -x "$script" ] || script="$self_root/$1"
printf '%s' "$input" | "$script"
