# Spike 007 prototype

Throwaway. Tries Laya and Jev as typed decision providers on ten hand-made test-bed cases, and compares them with the user's blind labels and with Claude reviewers. The results are in `../spec.md` (Log and Findings); this folder holds the evidence it cites.

The Python scripts need the `laya` package in a git-ignored `.venv/` and, for Jev, `TYPESAFE_API_KEY` in a git-ignored `.env`. Run them from this folder.

## Inputs

| File | What it holds |
| --- | --- |
| `cases/` | Five test-bed tasks, each with a requirements file and a good and a flawed patch; `cases/README.md` explains them |
| `cases/labels.json` | The user's blind scores, given before any model output was seen |

## Scripts

Every script is current; none replaces another. The first test asks one question per case, the second test asks four.

| Script | What it does | Writes |
| --- | --- | --- |
| `probe.py` | Checks truncation reporting, `max_len` limits, and latency per Laya checkpoint | Prints only |
| `tokens.py` | Counts the tokens of each case packet per tokenizer | Prints only |
| `trial_common.py` | Questions, cases, and the summary shared by `trial.py` and `jev_trial.py` | Nothing (library) |
| `trial.py` | First test, Laya: three checkpoints on the ten cases | `results/laya.json` |
| `jev_trial.py` | First test, Jev (`jev-1.13.0`) on the same cases and questions | `results/jev.json` |
| `battery.py` | Second test: four generic questions per case for Laya and Jev, mapped to 1–10, then the median | `results/battery.json` |
| `report_tasks.py` | The "Tasks and their flaws" section shared by both reports | Nothing (library) |
| `report.py` | Report of the first test: the user's labels against Laya, Jev, and Claude | `report.html` |
| `report2.py` | Report of the second test; Claude's scores come from the first test | `report2.html` |
| `review.py` | Side-by-side view of the ten patches, with no model output | `review.html` |

## Results

| File | From |
| --- | --- |
| `results/laya.json`, `results/jev.json` | First test |
| `results/claude.json`, `results/claude/` | Claude reviewers (a Sonnet council of three and one Opus reviewer), run by hand on anonymised packets |
| `results/claude-blind-key.json` | The key from `case-01`…`case-10` back to the case IDs |
| `results/battery.json` | Second test |

The HTML pages are generated, but they stay in the repository because the spec links to them.
