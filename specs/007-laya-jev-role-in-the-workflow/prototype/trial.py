"""Spike 007 trial: Laya checkpoints on the ten cases, compared with the user's blind labels.

code_quality_band uses the rubric of docs/definitions/sdlc-reference.yaml, asked in two band orders
to expose position bias. A noul risk check asks whether SQL is built from request input.
Writes results/laya.json and prints a summary.
"""
import json, time, laya

from trial_common import BANDS, INSTR, ORDERS, SQLQ, band, labels, packets, summarize

CHECKPOINTS = [("laya", "convaiinnovations/laya", None),
               ("typed-decisions", "convaiinnovations/laya-typed-decisions", None),
               ("multilingual-8192", "convaiinnovations/laya-multilingual", 8192)]

out = []
for name, model, max_len in CHECKPOINTS:
    a = laya.load(model)
    for case, state in packets.items():
        for order, ids in ORDERS.items():
            qs = {"band": {"type": "choice", "instructions": INSTR, "criteria": {i: BANDS[i] for i in ids}}}
            if case.startswith("T11") and order == "asc": qs["sql"] = SQLQ
            t = time.time(); r = a.predict(state, qs, max_len=max_len); ms = (time.time() - t) * 1000
            b = r["answers"]["band"]; u = r.get("usage", {})
            out.append({"checkpoint": name, "case": case, "order": order, "human": labels[case],
                        "human_band": band(labels[case]), "laya_band": b["choice"], "probs": b["probabilities"],
                        "answer_confidence": b["answer_confidence"], "sql_true": r["answers"].get("sql", {}).get("noul"),
                        "truncated": u.get("truncated"), "state_tokens": u.get("state_tokens"),
                        "dropped": u.get("state_tokens_dropped"), "ms": round(ms)})
    del a
json.dump(out, open("results/laya.json", "w"), indent=1)

summarize(out)
