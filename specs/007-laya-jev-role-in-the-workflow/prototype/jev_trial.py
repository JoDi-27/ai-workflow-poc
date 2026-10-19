"""Spike 007 trial: Jev (TypeSafe, hosted) on the same ten cases and questions as trial.py.
Reads TYPESAFE_API_KEY from .env and never prints it. Writes results/jev.json and prints a summary."""
import json, time, urllib.request, urllib.error
from trial_common import BANDS, INSTR, ORDERS, SQLQ, band, labels, packets, summarize

KEY = dict(l.strip().split("=", 1) for l in open(".env") if "=" in l)["TYPESAFE_API_KEY"]
MODEL = "jev-1.13.0"

def ask(state, questions):
    req = urllib.request.Request("https://api.typesafe.ai/v1/systemone", method="POST",
        data=json.dumps({"state": state, "model": MODEL, "questions": questions}).encode(),
        headers={"Authorization": f"Bearer {KEY}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r: return json.load(r)

out = []
for case, state in packets.items():
    for order, ids in ORDERS.items():
        qs = {"band": {"type": "choice", "instructions": INSTR, "criteria": {i: BANDS[i] for i in ids}}}
        if case.startswith("T11") and order == "asc": qs["sql"] = SQLQ
        t = time.time()
        try: r = ask(state, qs)
        except urllib.error.HTTPError as e:
            print("HTTP", e.code, case, order, e.read()[:300].decode(errors="replace")); raise SystemExit(1)
        ms = (time.time() - t) * 1000; b = r["answers"]["band"]
        out.append({"checkpoint": r.get("model", MODEL), "case": case, "order": order, "human": labels[case],
                    "human_band": band(labels[case]), "laya_band": b["choice"], "probs": b["probabilities"],
                    "answer_confidence": max(b["probabilities"].values()), "confidence": b.get("confidence"),
                    "sql_true": r["answers"].get("sql", {}).get("noul"), "truncated": False,
                    "input_tokens": r.get("usage", {}).get("input_tokens"), "ms": round(ms)})
json.dump(out, open("results/jev.json", "w"), indent=1)
summarize(out)
print("input tokens total:", sum(o["input_tokens"] or 0 for o in out))
