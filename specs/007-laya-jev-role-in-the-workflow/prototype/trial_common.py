"""Spike 007: questions, cases, and summary shared by trial.py (Laya) and jev_trial.py (Jev)."""
import json

BANDS = {"poor": "Does not meet the requirements, or is unreadable.",
         "weak": "Meets most requirements, with clear correctness or structure problems.",
         "good": "Meets the requirements; readable, with minor issues only.",
         "exemplary": "Meets the requirements; idiomatic, well-factored, and easy to extend."}
INSTR = ("Which band of the code quality rubric does this change fall in? "
         "Judge the change against the requirements, not against your own design.")
ORDERS = {"asc": ["poor", "weak", "good", "exemplary"], "desc": ["exemplary", "good", "weak", "poor"]}
SQLQ = {"type": "noul", "instructions": "Does the change build an SQL query by inserting request input into the SQL text?",
        "criteria": {"false": "SQL uses bind parameters or the repository API; request input never becomes SQL text.",
                     "true": "Request input is concatenated or formatted into the SQL string."}}
R = ["poor", "weak", "good", "exemplary"]
TASKS = ["T01", "T03", "T04", "T06", "T11"]

def band(score): return "poor" if score <= 3 else "weak" if score <= 5 else "good" if score <= 8 else "exemplary"

labels = {k: v for k, v in json.load(open("cases/labels.json")).items() if not k.startswith("_")}
packets = {c: "Requirements:\n" + open(f"cases/{c[:3]}-requirements.md").read()
              + "\n\nDiff:\n" + open(f"cases/{c}.patch").read() for c in labels}

def summarize(out):
    print(f"{'checkpoint':18} {'order':5} {'exact':>5} {'±1':>4} {'pairs':>5} {'trunc':>5} {'mean_conf':>9} {'p50ms':>6}  answers")
    for name in dict.fromkeys(o["checkpoint"] for o in out):
        for order in ORDERS:
            rows = [o for o in out if o["checkpoint"] == name and o["order"] == order]
            exact = sum(o["laya_band"] == o["human_band"] for o in rows)
            near = sum(abs(R.index(o["laya_band"]) - R.index(o["human_band"])) <= 1 for o in rows)
            ev = {o["case"]: sum(R.index(k) * p for k, p in o["probs"].items()) for o in rows}  # expected band index
            pairs = sum(ev[f"{t}-good"] > ev[f"{t}-flawed"] for t in TASKS)
            ms = sorted(o["ms"] for o in rows)[len(rows) // 2]
            print(f"{name:18} {order:5} {exact:3}/10 {near:2}/10 {pairs:3}/5 {sum(bool(o['truncated']) for o in rows):3}/10 "
                  f"{sum(o['answer_confidence'] for o in rows) / len(rows):9.2f} {ms:6}  {' '.join(o['laya_band'][:4] for o in rows)}")
    print("human bands:", " ".join(band(labels[c])[:4] for c in labels))
    for o in out:
        if o["sql_true"] is not None:
            print("noul sql", o["checkpoint"], o["case"], round(o["sql_true"], 3), "truncated" if o["truncated"] else "")
