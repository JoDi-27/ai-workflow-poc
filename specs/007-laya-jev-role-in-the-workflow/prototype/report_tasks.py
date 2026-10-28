"""Spike 007: the "Tasks and their flaws" section shared by report.py and report2.py.
Built from cases/Tnn-requirements.md, the flaw column of cases/README.md, and the variant table in docs/test-bed/README.md."""
import html, re
from trial_common import TASKS, labels

def _table_rows(text, first_cell):
    return [[c.strip() for c in l.strip().strip("|").split("|")] for l in text.splitlines() if re.match(rf"\|\s*{first_cell}\s*\|", l.strip())]

def _md(t):  # inline Markdown code spans only
    return re.sub(r"`([^`]+)`", r"<code>\1</code>", html.escape(t))

def tasks_section():
    readme = open("cases/README.md").read().split("## Build")[0]
    flaw = {r[0].replace(" ", "-"): r[3] for r in _table_rows(readme, "T\\d\\d (good|flawed)")}
    testbed = open("../../../docs/test-bed/README.md").read().split("## Laya's first cases")[1]
    variant = {r[0]: (r[1], r[2]) for r in _table_rows(testbed, "T\\d\\d")}
    out = ["<h2>Tasks and their flaws</h2><p>Five tasks from the test bed (<code>docs/test-bed/README.md</code>), each written twice against the "
           "<code>sample-crm</code> baseline: a good variant and a flawed one that differs only in its planted flaw. Both variants of a "
           "task share one requirements doc. Every variant builds and passes its own tests, so a test run alone never shows the flaw.</p>"
           "<table><tr><th>Task</th><th>What was asked</th><th>Good variant</th><th>Flawed variant: the flaw</th><th>Your scores</th></tr>"]
    for t in TASKS:
        req = open(f"cases/{t}-requirements.md").read()
        title = req.splitlines()[0].lstrip("# ").strip()
        ask = req.split("## Task", 1)[1].split("##", 1)[0].strip()
        acs = [l[2:] for l in req.split("## Acceptance criteria", 1)[1].splitlines() if l.startswith("- ")]
        crit = "".join(f"<li>{_md(a)}</li>" for a in acs)
        good, bad = variant.get(t, ("", ""))
        out.append(f"<tr><td><b>{html.escape(title)}</b></td><td>{_md(ask)}<details><summary class='p'>Acceptance criteria</summary>"
                   f"<ul class='p'>{crit}</ul></details></td><td>{_md(good)}</td>"
                   f"<td><b>{_md(bad)}</b><div class='p'>{_md(flaw.get(t + '-flawed', ''))}</div></td>"
                   f"<td>good {labels[t + '-good']} · flawed {labels[t + '-flawed']}</td></tr>")
    out.append("</table>")
    return "".join(out)
