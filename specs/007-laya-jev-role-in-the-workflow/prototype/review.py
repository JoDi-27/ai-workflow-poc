"""Spike 007: build review.html, a local side-by-side view of the ten case patches. Shows no model output."""
import glob, html, json, os
cases = []
for t in ["T01", "T03", "T04", "T06", "T11"]:
    req = open(f"cases/{t}-requirements.md").read()
    for v in ["good", "flawed"]:
        cases.append({"id": f"{t}-{v}", "req": req, "diff": open(f"cases/{t}-{v}.patch").read()})
page = """<!doctype html><html><head><meta charset="utf-8"><title>Laya cases review</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/diff2html@3.4.48/bundles/css/diff2html.min.css">
<script src="https://cdn.jsdelivr.net/npm/diff2html@3.4.48/bundles/js/diff2html-ui.min.js"></script>
<style>body{font-family:system-ui;margin:0;background:#fff;color:#111}nav{position:sticky;top:0;background:#f6f8fa;border-bottom:1px solid #ddd;padding:8px 16px;z-index:9}
nav a{margin-right:12px}section{padding:16px}pre.req{background:#f6f8fa;padding:12px;white-space:pre-wrap;border-radius:6px}
h2{border-top:3px solid #333;padding-top:12px}</style></head><body>
<nav>__NAV__ &nbsp;|&nbsp; <label><input type="checkbox" id="sbs" checked> side by side</label></nav><main id="m"></main>
<script>
const cases = __DATA__;
const m = document.getElementById('m');
function render(){ m.innerHTML=''; const sbs=document.getElementById('sbs').checked;
 for (const c of cases){ const s=document.createElement('section'); s.id=c.id;
  s.innerHTML=`<h2>${c.id}</h2><details open><summary>Requirements</summary><pre class="req"></pre></details><div class="d"></div>`;
  s.querySelector('.req').textContent=c.req; m.appendChild(s);
  new Diff2HtmlUI(s.querySelector('.d'), c.diff, {drawFileList:true, fileListToggle:true, matching:'lines',
     outputFormat: sbs?'side-by-side':'line-by-line', highlight:true}).draw(); } }
document.getElementById('sbs').onchange=render; render();
</script></body></html>"""
nav = " ".join(f'<a href="#{c["id"]}">{c["id"]}</a>' for c in cases)
open("review.html", "w").write(page.replace("__NAV__", nav).replace("__DATA__", json.dumps(cases).replace("</", "<\\/")))
print(os.path.abspath("review.html"))
