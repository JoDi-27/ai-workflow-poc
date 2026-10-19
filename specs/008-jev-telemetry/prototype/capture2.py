"""Spike 008, second Jev capture: headers-only identifiers, token accounting per question count, over-context
behaviour, a client timeout, and the model list. No rate-limit burst. Reads TYPESAFE_API_KEY from .env and never
prints or stores it. Writes results/capture2.json."""
import json, socket, time, urllib.request, urllib.error
KEY = dict(l.strip().split("=", 1) for l in open(".env") if "=" in l)["TYPESAFE_API_KEY"]
BASE = "https://api.typesafe.ai/v1"
def call(name, path="/systemone", body=None, extra=None, timeout=60, method="POST"):
    h = {"Content-Type": "application/json", "Authorization": f"Bearer {KEY}", **(extra or {})}
    req = urllib.request.Request(BASE + path, method=method, data=json.dumps(body).encode() if body else None, headers=h)
    t = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r: status, hdrs, raw = r.status, dict(r.headers), r.read()
    except urllib.error.HTTPError as e: status, hdrs, raw = e.code, dict(e.headers), e.read()
    except (urllib.error.URLError, socket.timeout, TimeoutError) as e:
        return {"case": name, "status": None, "ms": round((time.time() - t) * 1000), "error": repr(e)}
    ms = round((time.time() - t) * 1000)
    try: payload = json.loads(raw)
    except Exception: payload = raw[:500].decode(errors="replace")
    hdrs = {k: v for k, v in hdrs.items() if k.lower() not in ("set-cookie", "authorization")}
    return {"case": name, "status": status, "ms": ms, "headers": hdrs, "body": payload}
def q(n):
    qs = {"band": {"type": "choice", "instructions": "Which team owns this?", "criteria": {"billing": "Charges", "technical": "Bugs"}},
          "refund": {"type": "noul", "instructions": "Is a refund requested?"},
          "urgent": {"type": "noul", "instructions": "Is it urgent?"},
          "angry": {"type": "noul", "instructions": "Is the customer angry?"}}
    return dict(list(qs.items())[:n])
state = "My invoice shows a duplicate charge, please refund it."
# Roughly 4 characters per token: about 40k tokens and about 70k tokens of plain English.
long40 = ("The invoice service retried the charge after a gateway timeout and billed the card twice. " * 1800)
long70 = long40 * 2
cases = [
    call("headers only: X-Request-Id + traceparent", body={"state": state, "model": "jev-1.13.0", "questions": q(2)},
         extra={"X-Request-Id": "spike008-test-2", "traceparent": "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"}),
    call("tokens, 1 question", body={"state": state, "model": "jev-1.13.0", "questions": q(1)}),
    call("tokens, 2 questions", body={"state": state, "model": "jev-1.13.0", "questions": q(2)}),
    call("tokens, 4 questions", body={"state": state, "model": "jev-1.13.0", "questions": q(4)}),
    call("over 32k per question (~40k tokens, 1 question)", body={"state": long40, "model": "jev-1.13.0", "questions": q(1)}),
    call("over 64k per request (~70k tokens, 1 question)", body={"state": long70, "model": "jev-1.13.0", "questions": q(1)}),
    call("client timeout 50 ms", body={"state": state, "model": "jev-1.13.0", "questions": q(2)}, timeout=0.05),
    call("jev-preview", body={"state": state, "model": "jev-preview", "questions": q(2)}),
    call("GET /v1/models", path="/models", method="GET"),
]
json.dump(cases, open("results/capture2.json", "w"), indent=1)
for c in cases:
    print(f"== {c['case']}: HTTP {c['status']} in {c['ms']} ms")
    if "error" in c: print("   error:", c["error"]); continue
    print("   headers:", sorted(c["headers"]))
    print("   body:", json.dumps(c["body"])[:500])
