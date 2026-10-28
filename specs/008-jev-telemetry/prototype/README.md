# Spike 008 prototype

Throwaway. Captures what the engine can observe about each call to a typed decision provider: Jev (hosted) and, for comparison, `laya-serve` (local). The results are in `../spec.md` (Log and Findings); this folder holds the captures it cites.

The Jev scripts read `TYPESAFE_API_KEY` from a git-ignored `.env` and never print or store it. Run them from this folder.

## Scripts

Every script is current; each one is a separate capture, not a newer version of another.

| Script | What it captures | Writes |
| --- | --- | --- |
| `capture.py` | First Jev capture: status, headers, body, and timing for a success and a few failures | `results/capture.json` |
| `capture2.py` | Second Jev capture: identifiers sent as headers only, token accounting per question count, over-context behaviour, a client timeout, and the model list | `results/capture2.json` |
| `capture_laya.py` | `laya-serve` from spike 007's venv (path as the first argument): `/health`, a success, oversized states, a concurrency burst, and a bad model | `results/capture_laya.json` |

Raw responses (`results/raw-*`) and the `laya-serve` log are git-ignored.
