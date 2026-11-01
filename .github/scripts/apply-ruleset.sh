#!/usr/bin/env bash
# Apply or verify the main-branch rules (spec 015): the ruleset in .github/rulesets/main.json
# and the repository's merge settings. Needs gh, logged in as a repository admin, and jq.
#
# Usage: .github/scripts/apply-ruleset.sh [--verify] [--file <ruleset.json>]
#   (no option)  create the ruleset, or update it by name, then set the merge settings
#   --verify     compare GitHub's live rules and settings with the file; change nothing;
#                exit 1 on any difference
#   --file       use another ruleset file (T10 uses this to prove --verify notices drift)
#   --live       with --verify, read the live ruleset from a JSON file instead of GitHub and
#                skip the merge settings (for testing the comparison offline)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FILE="$ROOT/.github/rulesets/main.json"
VERIFY=0
LIVE=""
while [ $# -gt 0 ]; do
  case $1 in
    --verify) VERIFY=1 ;;
    --file) FILE=$2; shift ;;
    --live) LIVE=$2; shift ;;
    *) echo "usage: $0 [--verify] [--file <ruleset.json>] [--live <live.json>]" >&2; exit 2 ;;
  esac
  shift
done
cd "$ROOT"

API_VERSION="X-GitHub-Api-Version: 2026-03-10"
NAME=$(jq -r .name "$FILE")
ruleset_id() { gh api -H "$API_VERSION" "repos/{owner}/{repo}/rulesets" --jq ".[] | select(.name == \"$NAME\") | .id"; }

# The merge settings a ruleset cannot set itself: it only narrows what the repository allows.
SETTINGS='{"allow_merge_commit": true, "allow_squash_merge": false, "allow_rebase_merge": false,
           "merge_commit_title": "MERGE_MESSAGE", "merge_commit_message": "PR_TITLE"}'

if [ "$VERIFY" = 0 ]; then
  id=$(ruleset_id)
  if [ -z "$id" ]; then
    gh api -H "$API_VERSION" -X POST "repos/{owner}/{repo}/rulesets" --input "$FILE" --jq '"created ruleset \(.name) (\(.id))"'
  else
    gh api -H "$API_VERSION" -X PUT "repos/{owner}/{repo}/rulesets/$id" --input "$FILE" --jq '"updated ruleset \(.name) (\(.id))"'
  fi
  printf '%s' "$SETTINGS" | gh api -X PATCH "repos/{owner}/{repo}" --input - --jq '"merge settings: merge commit \(.allow_merge_commit), squash \(.allow_squash_merge), rebase \(.allow_rebase_merge)"'
  exit 0
fi

# --verify. GitHub may return parameters the file leaves out, so each live rule's
# parameters are cut down to the keys the file sets before comparing.
if [ -n "$LIVE" ]; then
  id=offline; live=$(cat "$LIVE")
else
  id=$(ruleset_id)
  [ -n "$id" ] || { echo "FAIL no ruleset named '$NAME' on GitHub"; exit 1; }
  live=$(gh api -H "$API_VERSION" "repos/{owner}/{repo}/rulesets/$id")
fi
diff_out=$(jq -n --argjson live "$live" --slurpfile f "$FILE" '
  ($f[0]) as $want
  | def norm($r): {enforcement: $r.enforcement, bypass_actors: ($r.bypass_actors // "missing"),
                   conditions: $r.conditions, rules: ($r.rules | sort_by(.type))};
    def project($rule):
      ([$want.rules[] | select(.type == $rule.type)][0].parameters) as $keys
      | if $keys == null then {type: $rule.type}
        else {type: $rule.type, parameters: ($rule.parameters // {} | with_entries(select(.key as $k | $keys | has($k))))}
        end;
  (norm($want)) as $w
  | (norm($live) | .rules |= map(project(.))) as $l
  | if $w == $l then empty
    else {want: $w, live: $l} end')
if [ -n "$diff_out" ]; then
  echo "FAIL ruleset '$NAME' differs from $FILE:"
  printf '%s\n' "$diff_out" | jq -S .
  exit 1
fi
echo "ok   ruleset '$NAME' ($id) matches $FILE"
[ -z "$LIVE" ] || exit 0

actual=$(gh api "repos/{owner}/{repo}" --jq '{allow_merge_commit, allow_squash_merge, allow_rebase_merge, merge_commit_title, merge_commit_message}')
if [ "$(printf '%s' "$actual" | jq -S -c .)" != "$(printf '%s' "$SETTINGS" | jq -S -c .)" ]; then
  echo "FAIL merge settings differ: $actual"
  exit 1
fi
echo "ok   merge settings"
