"""Spike 007, second test: a battery of four questions per case for Laya and Jev, each mapped to 1-10, then the median.

Same ten cases and the same state (requirements + full diff) as trial.py. The questions are generic: none names a
case's planted flaw. Writes results/battery.json. Claude is not rerun; report2.py reads its earlier scores.
"""
import json, statistics, time, urllib.request
import laya
from trial_common import BANDS, INSTR, ORDERS, labels, packets

QUESTIONS = {
    "criteria_met": {"type": "score",
        "instructions": "How completely does the code change implement the acceptance criteria in the requirements?",
        "criteria": ["Implements none of the acceptance criteria.",
                     "Implements a few of the acceptance criteria.",
                     "Implements most acceptance criteria, but at least one is missing or implemented wrongly.",
                     "Implements every acceptance criterion, with minor gaps.",
                     "Implements every acceptance criterion exactly."]},
    "tests_cover": {"type": "noul",
        "instructions": "Do the tests in the change check every acceptance criterion, including the edge cases the requirements name?",
        "criteria": {"false": "At least one acceptance criterion or named edge case has no test.",
                     "true": "Every acceptance criterion and named edge case has a test."}},
    "has_defect": {"type": "noul",
        "instructions": ("Does the change contain a defect: behavior that contradicts the requirements for some input, "
                         "a security flaw such as SQL injection, or a behavior change the requirements did not ask for?"),
        "criteria": {"false": "The code behaves as the requirements say for every input, with no security flaw and no unrequested behavior change.",
                     "true": "The code contradicts the requirements for some input, has a security flaw, or changes behavior that was not asked for."}},
    "code_quality_band": {"type": "choice", "instructions": INSTR, "criteria": {i: BANDS[i] for i in ORDERS["asc"]}},
}
MID = {"poor": 2.0, "weak": 4.5, "good": 7.0, "exemplary": 9.5}   # band midpoints on the 1-10 scale

def to_score(qid, ans):
    """Map one answer to 1-10, higher meaning better code."""
    if qid == "criteria_met":
        return 1 + 9 * ans["score"] / (len(QUESTIONS[qid]["criteria"]) - 1)
    if qid == "tests_cover":
        return 1 + 9 * ans["noul"]
    if qid == "has_defect":
        return 1 + 9 * (1 - ans["noul"])
    return sum(MID[k] * p for k, p in ans["probabilities"].items())

KEY = dict(l.strip().split("=", 1) for l in open(".env") if "=" in l)["TYPESAFE_API_KEY"]
def jev(state):
    req = urllib.request.Request("https://api.typesafe.ai/v1/systemone", method="POST",
        data=json.dumps({"state": state, "model": "jev-1.13.0", "questions": QUESTIONS}).encode(),
        headers={"Authorization": f"Bearer {KEY}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r:
        body = json.load(r)
    return body["answers"], {"truncated": False, "input_tokens": body.get("usage", {}).get("input_tokens")}

agent = laya.load("convaiinnovations/laya-multilingual")
def laya_ml(state):
    r = agent.predict(state, QUESTIONS, max_len=8192)
    return r["answers"], {"truncated": r["usage"].get("truncated"), "state_tokens": r["usage"].get("state_tokens")}

out = []
for model, ask in [("Laya multilingual (8192)", laya_ml), ("Jev 1.13", jev)]:
    for case, state in packets.items():
        t = time.time(); answers, usage = ask(state); ms = round((time.time() - t) * 1000)
        scores = {q: round(to_score(q, answers[q]), 2) for q in QUESTIONS}
        out.append({"model": model, "case": case, "human": labels[case], "answers": answers, "scores": scores,
                    "median": round(statistics.median(scores.values()), 2), "usage": usage, "ms": ms})
        print(f"{model:26} {case:11} you {labels[case]:2}  " + "  ".join(f"{q[:8]} {s:4.1f}" for q, s in scores.items())
              + f"  median {out[-1]['median']:4.1f}  {'CUT' if usage.get('truncated') else ''}")
json.dump({"questions": QUESTIONS, "band_midpoints": MID, "results": out}, open("results/battery.json", "w"), indent=1)
