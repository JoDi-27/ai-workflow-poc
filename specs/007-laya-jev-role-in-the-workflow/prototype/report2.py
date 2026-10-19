"""Spike 007: build report2.html for the second test (battery.py): four questions per case, mapped to 1-10, and their median.
The focus is whether each evaluator rates bad code low. Claude's scores come from the first test (results/claude.json), not a rerun."""
import json, html
from report_tasks import tasks_section
from trial_common import TASKS, band, labels
B = json.load(open("results/battery.json")); Q = B["questions"]; rows = B["results"]
council = json.load(open("results/claude.json"))["sonnet_council_median"]
PASS = 6
models = list(dict.fromkeys(o["model"] for o in rows))
get = {(o["model"], o["case"]): o for o in rows}
QNAMES = {"criteria_met": "Criteria met (score)", "tests_cover": "Tests cover criteria (noul)",
          "has_defect": "Has a defect (noul, inverted)", "code_quality_band": "Quality band (choice)", "median": "Median of the 4"}

def stats(score):  # score: case -> 1-10
    g = [score[f"{t}-good"] for t in TASKS]; f = [score[f"{t}-flawed"] for t in TASKS]
    return {"pairs": sum(a > b for a, b in zip(g, f)), "gap": sum(a - b for a, b in zip(g, f)) / 5,
            "caught": sum(b < PASS for b in f), "passed": sum(a >= PASS for a in g),
            "mae": sum(abs(score[c] - labels[c]) for c in labels) / 10, "band": sum(band(round(score[c])) == band(labels[c]) for c in labels)}
series = {"You (blind labels)": dict(labels), "Claude Sonnet council (first test, reference)": dict(council)}
for m in models:
    for q in list(Q) + ["median"]:
        series[f"{m} · {QNAMES[q]}"] = {c: (get[(m, c)]["median"] if q == "median" else get[(m, c)]["scores"][q]) for c in labels}

css = """body{font-family:system-ui;margin:24px;color:#111;background:#fff;max-width:1200px}table{border-collapse:collapse;margin:12px 0 28px}
th,td{border:1px solid #ddd;padding:6px 8px;vertical-align:top;font-size:14px}th{background:#f6f8fa;text-align:left}
.ok{background:#dcfce7}.off{background:#fee2e2}.p{color:#555;font-size:12px}.med{font-weight:600}tr.sep td{border-top:3px solid #333}
pre{background:#f6f8fa;padding:10px;white-space:pre-wrap;font-size:12px;border-radius:6px}h1{margin-bottom:4px}.sub{color:#555;margin-top:0}
.callout{border-left:4px solid #333;padding:8px 12px;background:#f6f8fa;margin:12px 0}"""
h = [f"""<!doctype html><html><head><meta charset="utf-8"><title>Question battery</title><style>{css}</style></head><body>
<h1>Second test: question battery for Laya and Jev</h1>
<p class="sub">Spike 007, 2026-10-19. The same ten cases and the same input (requirements + full diff) as the first test. Each model answers
four questions; each answer becomes a 1–10 score (higher = better code), and the case score is their median. Laya runs as
<code>laya-multilingual</code> at <code>max_len</code> 8192 (no input cut). Jev runs as <code>jev-1.13.0</code>. Claude was not rerun: its row is the
Sonnet council median from the first test, shown for reference. The pass mark is {PASS}, as in the <code>code_quality</code> step.</p>"""]

h.append(tasks_section())
s = {k: stats(v) for k, v in series.items()}
jd, jm, lm = s["Jev 1.13 · Has a defect (noul, inverted)"], s["Jev 1.13 · Median of the 4"], s["Laya multilingual (8192) · Median of the 4"]
h.append(f"""<div class="callout"><b>Does it rate bad code low?</b><br>
Laya: its median ranks good above flawed in {lm['pairs']}/5 pairs, but by at most {max(abs(series['Laya multilingual (8192) · Median of the 4'][t + '-good'] - series['Laya multilingual (8192) · Median of the 4'][t + '-flawed']) for t in TASKS):.1f} points (mean gap {lm['gap']:.1f}). It answers almost the same for both variants of every task. Its <i>has a defect</i> question "catches" every flawed variant only because it says "defect" for every case, good ones included. It does not tell bad code from good.<br>
Jev: its median ranks good above flawed in {jm['pairs']}/5 pairs (mean gap {jm['gap']:.1f}), but puts only {jm['caught']}/5 flawed variants below the pass mark.
Its <i>has a defect</i> question alone puts {jd['caught']}/5 flawed variants below {PASS} and keeps {jd['passed']}/5 good ones at {PASS} or above.
The lenient questions (criteria met, quality band) pull the median up, so a median hides the defect signal.</div>""")

h.append("""<h2>Summary</h2><table><tr><th>Evaluator · question</th><th>Good above flawed</th><th>Mean gap (good − flawed)</th>
<th>Flawed caught (&lt; 6)</th><th>Good passed (≥ 6)</th><th>Same band as you</th><th>Mean distance from your score</th></tr>""")
prev = None
for k, v in s.items():
    grp = k.split(" · ")[0]
    cls = ' class="sep"' if prev is not None and grp != prev else ""; prev = grp
    bold = ' class="med"' if "Median" in k or "You" in k or "Claude" in k else ""
    h.append(f"<tr{cls}><td{bold}>{html.escape(k)}</td><td>{v['pairs']}/5</td><td>{v['gap']:.1f}</td>"
             f"<td>{v['caught']}/5</td><td>{v['passed']}/5</td><td>{v['band']}/10</td><td>{v['mae']:.1f}</td></tr>")
h.append('</table><p class="p">"Flawed caught" and "Good passed" use the code_quality pass mark of 6. A band is taken from the rounded score '
         '(poor 1–3, weak 4–5, good 6–8, exemplary 9–10). Your own row is the reference for the last two columns.</p>')

h.append("<h2>Per case</h2><table><tr><th>Case</th><th>You</th><th>Claude council (ref.)</th>")
for m in models:
    h.append(f'<th colspan="5">{m}</th>')
h.append("</tr><tr><th></th><th></th><th></th>" + "".join(f"<th>{QNAMES[q].split(' (')[0]}</th>" for m in models for q in list(Q) + ["median"]) + "</tr>")
def c(v, case, med=False):
    good = case.endswith("good"); right = (v >= PASS) == good
    return f'<td class="{"ok" if right else "off"}{" med" if med else ""}">{v:.1f}</td>'
for case in labels:
    h.append(f'<tr{" class=sep" if case.endswith("good") and case != "T01-good" else ""}><td>{case}</td><td><b>{labels[case]}</b></td>{c(council[case], case)}'
             + "".join(
             "".join(c(get[(m, case)]["scores"][q], case) for q in Q) + c(get[(m, case)]["median"], case, True) for m in models) + "</tr>")
h.append('</table><p class="p">Green: on the right side of the pass mark (good ≥ 6, flawed &lt; 6). Red: on the wrong side.</p>')

h.append("<h2>The questions</h2><p>Each model got all four questions in one request per case. None names a case's planted flaw.</p><table><tr><th>Question</th><th>Type</th><th>Instructions and options</th><th>Mapped to 1–10 as</th></tr>")
mapping = {"criteria_met": "1 + 9 × level / 4 (level 0–4, from the expected position)",
           "tests_cover": "1 + 9 × P(true)", "has_defect": "1 + 9 × (1 − P(true)): a likely defect means a low score",
           "code_quality_band": "Expected value of the band midpoints: poor 2, weak 4.5, good 7, exemplary 9.5"}
for q, d in Q.items():
    crit = d["criteria"]; opts = "<br>".join(f"{i}: {html.escape(t)}" for i, t in (enumerate(crit) if isinstance(crit, list) else crit.items()))
    h.append(f"<tr><td><code>{q}</code></td><td>{d['type']}</td><td>{html.escape(d['instructions'])}<div class='p'>{opts}</div></td><td>{mapping[q]}</td></tr>")
h.append("</table><h2>Input</h2><p>The <code>state</code> for each case is the same text as in the first test: <code>Requirements:</code>, the task's "
         "requirements doc, then <code>Diff:</code> and the full unified diff against <code>baseline</code>, tests included. The first report "
         "(<code>report.html</code>, section \"What each evaluator was given\") shows the full state for every case.</p>"
         "<p class='p'>Raw answers, mapped scores, and latency: <code>results/battery.json</code>. Script: <code>battery.py</code>.</p></body></html>")
open("report2.html", "w").write("\n".join(h))
