#!/bin/sh
# Spike 005, G4: runs the evaluator in a Linux container with a `claude setup-token` token.
# Run it yourself:  CLAUDE_CODE_OAUTH_TOKEN=<token> ./container-trial.sh
# The token goes only into the containers' environment. Results land in ./out/ (gitignored).
set -u
cd "$(dirname "$0")"
: "${CLAUDE_CODE_OAUTH_TOKEN:?set CLAUDE_CODE_OAUTH_TOKEN to the token from claude setup-token}"
mkdir -p out work && rm -rf work/repo && cp -R fixture work/repo
docker build -q -t spike005-evaluator . >/dev/null
run() { docker run --rm -e CLAUDE_CODE_OAUTH_TOKEN -v "$PWD/work/repo:/work:ro" "$@"; }

echo "1. --bare with the token (expected: not logged in)"
run --entrypoint claude spike005-evaluator -p --bare --output-format json "Say OK" > out/bare.json 2>&1
jq -c '{is_error, result}' out/bare.json

echo "2. one isolated evaluation (Sonnet)"
start=$(date +%s); run spike005-evaluator /work > out/single.json 2>&1
jq -c '{is_error, structured_output, cost: .total_cost_usd, turns: .num_turns}' out/single.json; echo "   wall $(( $(date +%s) - start ))s"

echo "3. council of 3 in parallel"
start=$(date +%s)
for i in 1 2 3; do run spike005-evaluator /work > "out/council-$i.json" 2>&1 & done; wait
for i in 1 2 3; do jq -c '{is_error, raw_score: .structured_output.raw_score, cost: .total_cost_usd}' "out/council-$i.json"; done
echo "   wall $(( $(date +%s) - start ))s"
echo "Done. Tell Claude to read prototype/evaluator/out/."
