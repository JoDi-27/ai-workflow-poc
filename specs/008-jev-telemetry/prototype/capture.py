"""Spike 008: capture what one Jev call exposes: status, headers, body, timing, for a success and a few failures.
Reads TYPESAFE_API_KEY from .env and never prints or stores it."""
import json, time, urllib.request, urllib.error
KEY = dict(l.strip().split("=", 1) for l in open(".env") if "=" in l)["TYPESAFE_API_KEY"]
URL = "https://api.typesafe.ai/v1/systemone"
Q = {"band": {"type": "choice", "instructions": "Which team owns this?", "criteria": {"billing": "Charges", "technical": "Bugs"}},
     "refund": {"type": "noul", "instructions": "Is a refund requested?"}}
def call(name, body, key=KEY, extra=None):
    h = {"Content-Type": "application/json", **({"Authorization": f"Bearer {key}"} if key else {}), **(extra or {})}
    req = urllib.request.Request(URL, method="POST", data=json.dumps(body).encode(), headers=h)
    t = time.time()
    try:
        with urllib.request.urlopen(req, timeout=60) as r: status, hdrs, raw = r.status, dict(r.headers), r.read()
    except urllib.error.HTTPError as e: status, hdrs, raw = e.code, dict(e.headers), e.read()
    ms = round((time.time() - t) * 1000)
    try: payload = json.loads(raw)
    except Exception: payload = raw[:500].decode(errors="replace")
    hdrs = {k: v for k, v in hdrs.items() if k.lower() not in ("set-cookie", "authorization")}
    return {"case": name, "status": status, "ms": ms, "headers": hdrs, "body": payload}
state = "My invoice shows a duplicate charge, please refund it."
cases = [
    call("ok, jev-1.13.0", {"state": state, "model": "jev-1.13.0", "questions": Q}),
    call("ok, jev-latest", {"state": state, "model": "jev-latest", "questions": Q}),
    call("ok, client metadata fields + request-id header", {"state": state, "model": "jev-1.13.0", "questions": Q,
         "metadata": {"run": "r1", "step": "code_quality"}}, extra={"X-Request-Id": "spike008-test-1", "traceparent": "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"}),
    call("unknown model", {"state": state, "model": "jev-0.0.0", "questions": Q}),
    call("bad question", {"state": state, "model": "jev-1.13.0", "questions": {"x": {"type": "choice", "instructions": "?"}}}),
    call("no key", {"state": state, "model": "jev-1.13.0", "questions": Q}, key=None),
    call("bad key", {"state": state, "model": "jev-1.13.0", "questions": Q}, key="invalid-key"),
]
json.dump(cases, open("results/capture.json", "w"), indent=1)
for c in cases:
    print(f"== {c['case']}: HTTP {c['status']} in {c['ms']} ms")
    print("   headers:", sorted(c["headers"]))
    print("   body:", json.dumps(c["body"])[:400])
