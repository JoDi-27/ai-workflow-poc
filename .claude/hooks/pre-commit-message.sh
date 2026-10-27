#!/usr/bin/env bash
# PreToolUse on Bash: block `git commit` whose message does not start with `[NNN]` or `[NNN] Tn`.
# Exit 2 blocks the call and sends stderr to Claude.
input=$(cat)
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty')
printf '%s' "$cmd" | grep -Eq '(^|[;&|(])[[:space:]]*git[[:space:]]+(-[^[:space:]]+[[:space:]]+)*commit([[:space:]]|$)' || exit 0

# Amend without a new message keeps the old one.
if printf '%s' "$cmd" | grep -Eq -- '--no-edit'; then exit 0; fi

# First line of the message: a heredoc body, else -m / -am / --message.
msg=$(printf '%s' "$cmd" | python3 -c '
import re, shlex, sys
cmd = sys.stdin.read()
m = re.search(r"<<-?\s*[\x27\"]?(\w+)[\x27\"]?\n(.*?)\n\s*\1", cmd, re.S)
if m:
    for line in m.group(2).splitlines():
        if line.strip():
            print(line.strip())
            break
    sys.exit(0)
try:
    t = shlex.split(cmd)
except ValueError:
    sys.exit(0)
for i, a in enumerate(t):
    if a.startswith("--message="):
        print(a[10:].splitlines()[0] if a[10:] else "")
        break
    if (a == "--message" or re.fullmatch(r"-[a-zA-Z]*m", a)) and i + 1 < len(t):
        print(t[i + 1].splitlines()[0] if t[i + 1] else "")
        break
')

# No message found (editor, -F, -C): let git handle it.
[ -n "$msg" ] || exit 0
if printf '%s' "$msg" | grep -Eq '^\[[0-9]{3}\]( T[0-9]+)?( |$)'; then exit 0; fi
echo "Commit message must start with the spec ID, e.g. '[003] T2 Add run state table' or '[003] Close spike'. Got: $msg" >&2
exit 2
