#!/usr/bin/env bash
# Checks the planted baseline behaviors of sample-crm (spec 004, AC-4 and AC-8).
#
# Usage: scripts/baseline-checks.sh [BASE_URL]
#
# BASE_URL defaults to http://localhost:8080. The service must run with an empty
# database, for example `./mvnw spring-boot:test-run` in sample-crm. The script
# creates contacts and leaves them there. It needs bash, curl, and jq.
# It prints one PASS or FAIL line per check and exits non-zero on any failure.

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
failures=0

# Posts a JSON body and prints the response body. Fails unless the status is 201.
create_contact() {
  local body="$1" out status
  out=$(curl -sS -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' \
    --data "$body" "$BASE_URL/contacts")
  status="${out##*$'\n'}"
  if [[ "$status" != "201" ]]; then
    echo "  POST /contacts answered $status: ${out%$'\n'*}" >&2
    return 1
  fi
  printf '%s' "${out%$'\n'*}"
}

result() {
  local name="$1" ok="$2" detail="$3"
  if [[ "$ok" == "true" ]]; then
    echo "PASS $name: $detail"
  else
    echo "FAIL $name: $detail"
    failures=$((failures + 1))
  fi
}

total=$(curl -sS -f "$BASE_URL/contacts" | jq '.total')
if [[ "$total" != "0" ]]; then
  echo "The database is not empty ($total contacts). Restart the service on an empty database." >&2
  exit 2
fi

# AC-4: with five contacts and page size 2, page 2 starts with page 1's second contact.
for i in 1 2 3 4 5; do
  create_contact "{\"firstName\":\"Page$i\",\"lastName\":\"Check\",\"email\":\"page$i@check.example\"}" >/dev/null
done
page1=$(curl -sS -f "$BASE_URL/contacts?page=1&size=2" | jq -c '[.items[].id]')
page2=$(curl -sS -f "$BASE_URL/contacts?page=2&size=2" | jq -c '[.items[].id]')
ok=$(jq -n --argjson a "$page1" --argjson b "$page2" '($a | length) == 2 and ($b | length) > 0 and $b[0] == $a[1]')
result "AC-4 page 2 starts with the last contact of page 1" "$ok" "page 1 ids $page1, page 2 ids $page2"

# AC-8: two emails that differ only in case and surrounding whitespace are both
# accepted and each is read back exactly as sent.
first_email=' Ana@Example.com '
second_email='ana@example.com'
first=$(create_contact "{\"firstName\":\"Ana\",\"lastName\":\"Spaced\",\"email\":\"$first_email\"}") || first='{}'
second=$(create_contact "{\"firstName\":\"Ana\",\"lastName\":\"Plain\",\"email\":\"$second_email\"}") || second='{}'
read_first=$(curl -sS "$BASE_URL/contacts/$(jq -r '.id' <<<"$first")" | jq -r '.email // empty')
read_second=$(curl -sS "$BASE_URL/contacts/$(jq -r '.id' <<<"$second")" | jq -r '.email // empty')
if [[ "$read_first" == "$first_email" && "$read_second" == "$second_email" ]]; then ok=true; else ok=false; fi
result "AC-8 emails stored as sent, duplicates accepted" "$ok" "read back \"$read_first\" and \"$read_second\""

exit $((failures > 0))
