#!/usr/bin/env bash
# PreToolUse on Bash: block `git commit` whose message does not start with `[NNN]`, `[NNN] Tn`, or `[meta]`, or whose first line is over 72 characters.
# Exit 2 blocks the call and sends stderr to Claude.
input=$(cat)
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty')
printf '%s' "$cmd" | grep -Eq '(^|[;&|(])[[:space:]]*git[[:space:]]+(-[^[:space:]]+[[:space:]]+)*commit([[:space:]]|$)' || exit 0

# Amend without a new message keeps the old one.
if printf '%s' "$cmd" | grep -Eq -- '--no-edit'; then exit 0; fi

# First line of the message, from the -m / -am / --message argument of the `git commit` itself.
# Only that command is read, so a heredoc or -m elsewhere in the same Bash call does not count.
# A `-m "$(cat <<'EOF' ... EOF)"` value is a heredoc: its body is the message.
msg=$(printf '%s' "$cmd" | python3 -c '
import re, shlex, sys
cmd = sys.stdin.read()
m = re.search(r"(^|[;&|(])\s*git\s+(-\S+\s+)*commit(\s|$)", cmd, re.M)
if not m:
    sys.exit(0)
lex = shlex.shlex(cmd[m.end(1):], posix=True, punctuation_chars=";&|")
lex.whitespace_split = True
try:
    t = list(lex)
except ValueError:
    sys.exit(0)
value = None
for i, a in enumerate(t):
    if a in (";", "&", "&&", "|", "||"):
        break
    if a.startswith("--message="):
        value = a[10:]
        break
    if (a == "--message" or re.fullmatch(r"-[a-zA-Z]*m", a)) and i + 1 < len(t):
        value = t[i + 1]
        break
if value is None:
    sys.exit(0)
h = re.search(r"<<-?\s*[\x27\"]?(\w+)[\x27\"]?[^\n]*\n(.*?)\n\s*\1(\s|$)", value, re.S)
if h:
    value = h.group(2)
for line in value.splitlines():
    if line.strip():
        print(line.strip())
        break
')

# No message found (editor, -F, -C): let git handle it.
[ -n "$msg" ] || exit 0
if ! printf '%s' "$msg" | grep -Eq '^(\[meta\]|\[[0-9]{3}\]( T[0-9]+)?)( |$)'; then
  echo "Commit message must start with the spec ID, e.g. '[003] T2 Add run state table', '[003] Close spike', or '[meta] Fix hook' for housekeeping with no spec. Got: $msg" >&2
  exit 2
fi
# Keep the first line concise (CONTRIBUTING.md); characters, not bytes.
n=$(printf '%s' "$msg" | python3 -c 'import sys; print(len(sys.stdin.read()))')
if [ "$n" -gt 72 ]; then
  echo "Commit message first line is $n characters; keep it to 72 or fewer and put any detail in the body (CONTRIBUTING.md). Got: $msg" >&2
  exit 2
fi
exit 0
