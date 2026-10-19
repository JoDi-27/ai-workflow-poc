"""Spike 007: build report.html, a per-case view of the user's labels against the Laya and Jev trial results."""
import json, html
from report_tasks import tasks_section
from trial_common import R, TASKS, band, labels
rows = json.load(open("results/laya.json")) + json.load(open("results/jev.json"))
names = list(dict.fromkeys(o["checkpoint"] for o in rows))
nice = {"laya": "Laya base (512)", "typed-decisions": "Laya typed-decisions (1024)",
        "multilingual-8192": "Laya multilingual (8192)", "jev-1.13.0": "Jev 1.13"}
def cell(o):
    d = abs(R.index(o["laya_band"]) - R.index(o["human_band"]))
    cls = "ok" if d == 0 else "near" if d == 1 else "off"
    probs = " · ".join(f"{k[:4]} {v:.2f}" for k, v in sorted(o["probs"].items(), key=lambda kv: R.index(kv[0])))
    cut = ' <span class="cut">input cut</span>' if o["truncated"] else ""
    return f'<td class="{cls}"><b>{o["laya_band"]}</b>{cut}<div class="p">{probs}</div></td>'
def summary(name, order):
    rs = [o for o in rows if o["checkpoint"] == name and o["order"] == order]
    exact = sum(o["laya_band"] == o["human_band"] for o in rs)
    near = sum(abs(R.index(o["laya_band"]) - R.index(o["human_band"])) <= 1 for o in rs)
    ev = {o["case"]: sum(R.index(k) * p for k, p in o["probs"].items()) for o in rs}
    pairs = sum(ev[f"{t}-good"] > ev[f"{t}-flawed"] for t in TASKS)
    return exact, near, pairs, sum(bool(o["truncated"]) for o in rs), sorted(o["ms"] for o in rs)[len(rs) // 2]
h = ["""<!doctype html><html><head><meta charset="utf-8"><title>Laya and Jev trial</title><style>
body{font-family:system-ui;margin:24px;color:#111;background:#fff}table{border-collapse:collapse;margin:12px 0 28px}
th,td{border:1px solid #ddd;padding:6px 8px;vertical-align:top;font-size:14px}th{background:#f6f8fa;text-align:left}
.ok{background:#dcfce7}.near{background:#fef9c3}.off{background:#fee2e2}.p{color:#555;font-size:11px;margin-top:2px}
pre.req{background:#f6f8fa;padding:10px;white-space:pre-wrap;font-size:12px;border-radius:6px;max-height:480px;overflow:auto}
details{margin:4px 0}summary{cursor:pointer;font-size:14px}code{font-size:13px}
.cut{font-size:11px;background:#e5e7eb;border-radius:4px;padding:0 4px}h1{margin-bottom:4px}.sub{color:#555;margin-top:0}
</style></head><body><h1>Laya and Jev trial: code_quality_band</h1>
<p class="sub">Spike 007, 2026-10-19. Ten hand-made cases, labelled blind by the user. The question is asked in two band orders
(ascending and descending) to show position bias. Green: same band as you. Yellow: one band off. Red: two or more off.</p>
<h2>Summary</h2><table><tr><th>Model</th><th>Band order</th><th>Same band as you</th><th>Within one band</th>
<th>Rates good above flawed</th><th>Input cut</th><th>Median latency</th></tr>"""]
h[0] = h[0].replace("<h2>Summary</h2>", tasks_section() + "<h2>Summary</h2>")
for n in names:
    for order in ("asc", "desc"):
        e, nr, p, t, ms = summary(n, order)
        h.append(f"<tr><td>{nice.get(n, n)}</td><td>{order}</td><td>{e}/10</td><td>{nr}/10</td><td>{p}/5</td><td>{t}/10</td><td>{ms} ms</td></tr>")
h.append('<tr><td colspan="7" class="p">For comparison: always answering one of the most common bands gets 3/10; guessing ranks 2.5 of 5 pairs.</td></tr></table>')
import os
council = json.load(open("results/claude.json"))["sonnet_council_median"] if os.path.exists("results/claude.json") else None
def council_cell(c):
    d = abs(R.index(band(council[c])) - R.index(band(labels[c])))
    cls = "ok" if d == 0 else "near" if d == 1 else "off"
    return f'<td class="{cls}"><b>{band(council[c])}</b><div class="p">score {council[c]:g} (median of 3 Sonnet)</div></td>'
for order in ("asc", "desc"):
    h.append(f"<h2>Per case, band order {order}</h2><table><tr><th>Case</th><th>Your score</th>"
             + ("<th>Claude council</th>" if council else "")
             + "".join(f"<th>{nice.get(n, n)}</th>" for n in names) + "</tr>")
    for c in labels:
        h.append(f"<tr><td>{c}</td><td><b>{labels[c]}</b> ({band(labels[c])})</td>"
                 + (council_cell(c) if council else "")
                 + "".join(cell(next(o for o in rows if o["checkpoint"] == n and o["case"] == c and o["order"] == order)) for n in names) + "</tr>")
    h.append("</table>")
    if council and order == "asc":
        h.append('<p class="p">The Claude council does not depend on band order; it is repeated in both tables for comparison.</p>')
h.append('<h2>Risk check on T11: "Is request input put into the SQL text?"</h2><table><tr><th>Model</th><th>T11-good (safe)</th><th>T11-flawed (injectable)</th></tr>')
for n in names:
    v = {o["case"]: o for o in rows if o["checkpoint"] == n and o["sql_true"] is not None}
    f = lambda o: f'{o["sql_true"]:.2f}' + (' <span class="cut">input cut</span>' if o["truncated"] else "")
    h.append(f"<tr><td>{nice.get(n, n)}</td><td>{f(v['T11-good'])}</td><td>{f(v['T11-flawed'])}</td></tr>")
h.append('<tr><td colspan="3" class="p">Probability of "yes". Ideal: near 0 for the safe version and near 1 for the injectable one.</td></tr></table></body></html>')

# What each evaluator was given (trial_common.py builds the Laya and Jev input; the Claude prompt is the one the reviewers got).
from trial_common import BANDS, INSTR, ORDERS, SQLQ, packets
pre = lambda t: f'<pre class="req">{html.escape(t)}</pre>'
def tokens(c):
    by = {o["checkpoint"]: o for o in rows if o["case"] == c and o["order"] == "asc"}
    return " · ".join(f'{nice.get(n, n)}: {by[n].get("state_tokens") or by[n].get("input_tokens")} tokens'
                      + (f' ({by[n]["dropped"]} dropped)' if by[n].get("dropped") else "") for n in names)
q_asc = {"band": {"type": "choice", "instructions": INSTR, "criteria": {i: BANDS[i] for i in ORDERS["asc"]}}}
q_desc = {"band": {"type": "choice", "instructions": INSTR, "criteria": {i: BANDS[i] for i in ORDERS["desc"]}}}
CLAUDE_PROMPT = """You are a code-quality evaluator. Score ten independent code changes.
[Read only the ten anonymised case files case-01.md ... case-10.md; nothing else on disk.]

Each file holds the requirements for a change to a Spring Boot CRM service and the change as a unified diff.
Judge each change on its own, against its requirements, not against your own design. Some cases may share
requirements; still score each independently.

Score each on a scale of 1-10 using this rubric:
- poor (1-3): Does not meet the requirements, or is unreadable.
- weak (4-5): Meets most requirements, with clear correctness or structure problems.
- good (6-8): Meets the requirements; readable, with minor issues only.
- exemplary (9-10): Meets the requirements; idiomatic, well-factored, and easy to extend.
Consider correctness against every acceptance criterion, whether the tests actually cover the behavior required,
security, and behavior changes the requirements did not ask for.

Reply with ONLY a JSON object: {"case-01": {"score": <int>, "reason": "<one sentence>"}, ...}"""
h.append(h.pop().replace("</body></html>", ""))
h.append("""<h2>What each evaluator was given</h2>
<p>All evaluators saw the same case content: the task's requirements doc followed by the full unified diff against <code>baseline</code>,
tests included. They differ in the instructions around it.</p>
<table><tr><th></th><th>Laya</th><th>Jev</th><th>Claude reviewers</th></tr>
<tr><td>How</td><td><code>laya.load(checkpoint).predict(state, questions, max_len=…)</code> locally on MPS</td>
<td><code>POST https://api.typesafe.ai/v1/systemone</code>, model <code>jev-1.13.0</code></td>
<td>Subagents (3 × Sonnet, 1 × Opus), each reading all ten anonymised, shuffled case files</td></tr>
<tr><td>Window</td><td>base: 512 · typed-decisions: 1024 · multilingual: 8192 (<code>max_len</code>)</td><td>64k per request</td><td>Whole files</td></tr>
<tr><td>Instructions</td><td colspan="2">The question below: the rubric's four band descriptors only, with no score ranges and no extra guidance</td>
<td>Band descriptors <b>plus</b> the 1–10 ranges and an explicit list of what to consider (acceptance criteria, test coverage, security, unrequested behavior changes)</td></tr>
<tr><td>Answer</td><td colspan="2">A band, with probabilities over the four bands</td><td>A 1–10 score and a one-sentence reason</td></tr></table>
<p class="p">So the Claude reviewers got richer instructions than Laya and Jev. That is how the runner's evaluator is designed to work,
but it means the comparison is not instruction-for-instruction.</p>
<h3>Laya and Jev: the question (ascending band order)</h3>""" + pre(json.dumps(q_asc, indent=2))
         + "<h3>Descending band order (same text, options reversed)</h3>" + pre(json.dumps(q_desc, indent=2))
         + "<h3>Extra question on the two T11 cases (ascending run only)</h3>" + pre(json.dumps({"sql": SQLQ}, indent=2))
         + "<h3>Laya and Jev: the state, per case</h3><p>This exact text was the <code>state</code>. Click a case to see it.</p>")
for c in labels:
    h.append(f'<details><summary><b>{c}</b> · {html.escape(tokens(c))}</summary>{pre(packets[c])}</details>')
h.append("<h3>Claude reviewers: the prompt</h3>" + pre(CLAUDE_PROMPT)
         + "<p>Each case file held <code># Requirements</code>, the same requirements doc, then <code># Change</code> with the same diff in a <code>```diff</code> block.</p></body></html>")

# Claude reviewers (blind, shuffled packets; see results/claude.json), compared with every other evaluator.
import os, statistics as st
if os.path.exists("results/claude.json"):
    h.append(h.pop().replace("</body></html>", ""))
    cj = json.load(open("results/claude.json")); rev = cj["reviewers"]; council = cj["sonnet_council_median"]
    human = {c: band(v) for c, v in labels.items()}
    def asc(cp): return {o["case"]: o for o in rows if o["checkpoint"] == cp and o["order"] == "asc"}
    ev = lambda o: sum(R.index(k) * p for k, p in o["probs"].items())
    evals = {"Claude Sonnet council (median of 3)": ({c: band(v) for c, v in council.items()}, council),
             "Claude Opus (1 reviewer)": ({c: band(v) for c, v in rev["opus"].items()}, rev["opus"])}
    for n in names:
        a = asc(n); evals[nice.get(n, n)] = ({c: a[c]["laya_band"] for c in labels}, {c: ev(a[c]) for c in labels})
    cb = evals["Claude Sonnet council (median of 3)"][0]
    h.append('<h2>All evaluators, quality only (ascending band order for Laya and Jev)</h2><table><tr><th>Evaluator</th>'
             '<th>Same band as you</th><th>Within one band</th><th>Rates good above flawed</th><th>Same band as the Claude council</th></tr>')
    for m, (b, o) in evals.items():
        h.append(f"<tr><td>{m}</td><td>{sum(b[c] == human[c] for c in labels)}/10</td>"
                 f"<td>{sum(abs(R.index(b[c]) - R.index(human[c])) <= 1 for c in labels)}/10</td>"
                 f"<td>{sum(o[t + '-good'] > o[t + '-flawed'] for t in TASKS)}/5</td><td>{sum(b[c] == cb[c] for c in labels)}/10</td></tr>")
    h.append("</table><h2>Claude reviewer scores per case (1–10)</h2><table><tr><th>Case</th><th>You</th><th>Sonnet 1</th>"
             "<th>Sonnet 2</th><th>Sonnet 3</th><th>Council median</th><th>Opus</th><th>Opus's reason</th></tr>")
    reasons = {key: v["reason"] for key, v in json.load(open("results/claude/opus.json")).items()}
    bkey = {v: k for k, v in json.load(open("results/claude-blind-key.json")).items()}
    for c in labels:
        d = abs(council[c] - labels[c]); cls = "ok" if d <= 1 else "near" if d <= 2 else "off"
        h.append(f"<tr><td>{c}</td><td><b>{labels[c]}</b></td>" + "".join(f"<td>{rev[r][c]}</td>" for r in ["sonnet-1", "sonnet-2", "sonnet-3"])
                 + f'<td class="{cls}"><b>{council[c]:g}</b></td><td>{rev["opus"][c]}</td><td class="p">{html.escape(reasons[bkey[c]])}</td></tr>')
    h.append('<tr><td colspan="8" class="p">Council cell colour: green within 1 point of you, yellow 2, red 3 or more.</td></tr></table>')
    h.append("</body></html>")
open("report.html", "w").write("\n".join(h))
