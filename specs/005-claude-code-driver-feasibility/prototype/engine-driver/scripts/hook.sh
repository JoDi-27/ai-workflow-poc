#!/bin/sh
# Forwards a hook's stdin JSON to the engine and prints the engine's answer as the hook's output.
# Exits non-zero when the engine is unreachable or answers non-2xx, so hooks with onFailure "block" fail closed.
# For SessionStart it adds the kit's generic instructions in front of the engine's status.
event="$1"
url="${CLAUDE_PLUGIN_OPTION_ENGINE_URL:-${ENGINE_URL:-http://localhost:8787}}"
token="${CLAUDE_PLUGIN_OPTION_ENGINE_TOKEN:-$ENGINE_TOKEN}"
out=$(curl -sS --fail --max-time 8 -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
  --data-binary @- "$url/hooks/$event") || { echo "engine unreachable for $event hook" >&2; exit 1; }
if [ "$event" = "session-start" ]; then
  printf '%s' "$out" | jq --rawfile kit "${CLAUDE_PLUGIN_ROOT}/instructions.md" \
    '.hookSpecificOutput.additionalContext = ($kit + "\n\n" + .hookSpecificOutput.additionalContext)'
else
  printf '%s' "$out"
fi
