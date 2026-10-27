#!/usr/bin/env bash
# Stop: warn (never block) when this session still holds sdd claims.
input=$(cat)
sid=$(printf '%s' "$input" | jq -r '.session_id // empty')
[ -n "$sid" ] || exit 0
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
common=$(git rev-parse --git-common-dir 2>/dev/null) || exit 0
dir="$(cd "$common" && pwd)/sdd-claims"
[ -d "$dir" ] || exit 0
held=""
for c in "$dir"/*/; do
  [ -f "$c/owner" ] || continue
  name=$(basename "$c")
  case "$name" in id-*) continue ;; esac   # ID reservations, not held work
  [ -f "$c/done" ] && continue             # finished tasks
  [ "$(sed -n 's/^owner=//p' "$c/owner" | head -1)" = "$sid" ] && held="$held $name"
done
[ -n "$held" ] || exit 0
msg="This session still holds sdd claims:$held. Release with: specs/_kit/sdd release <ID> [Tn] [--done]"
jq -n --arg m "$msg" '{systemMessage: $m}'
exit 0
