"""Spike 008: capture what laya-serve exposes per call: /health with and without the key, a success, an
over-window state, a state over 50,000 characters, a concurrency burst, and a bad model. Starts laya-serve from
spike 007's venv (path in argv[1]) on a local port with a throwaway key, and writes results/capture_laya.json."""
import json, os, secrets, subprocess, sys, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor
VENV = sys.argv[1]
PORT, KEY = 8765, secrets.token_hex(16)
URL = f"http://127.0.0.1:{PORT}"
env = {**os.environ, "LAYA_PORT": str(PORT), "LAYA_HOST": "127.0.0.1", "LAYA_MODELS": "multilingual",
       "LAYA_API_KEY": KEY, "LAYA_LOG_LEVEL": "warning"}
srv = subprocess.Popen([f"{VENV}/bin/laya-serve"], env=env, stdout=open("results/laya-serve.log", "w"), stderr=subprocess.STDOUT)
def call(name, path="/v1/systemone", body=None, key=KEY, timeout=300):
    h = {"Content-Type": "application/json", **({"Authorization": f"Bearer {key}"} if key else {})}
    req = urllib.request.Request(URL + path, method="POST" if body is not None else "GET",
                                 data=json.dumps(body).encode() if body is not None else None, headers=h)
    t = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r: status, hdrs, raw = r.status, dict(r.headers), r.read()
    except urllib.error.HTTPError as e: status, hdrs, raw = e.code, dict(e.headers), e.read()
    except Exception as e: return {"case": name, "status": None, "ms": round((time.time() - t) * 1000), "error": repr(e)}
    try: payload = json.loads(raw)
    except Exception: payload = raw[:500].decode(errors="replace")
    return {"case": name, "status": status, "ms": round((time.time() - t) * 1000), "headers": hdrs, "body": payload}
try:
    t0 = time.time()
    while True:
        try:
            urllib.request.urlopen(URL + "/health", timeout=2); break
        except Exception:
            if srv.poll() is not None or time.time() - t0 > 600: raise SystemExit("laya-serve did not start; see results/laya-serve.log")
            time.sleep(2)
    startup_s = round(time.time() - t0, 1)
    Q = {"band": {"type": "choice", "instructions": "Which team owns this?", "criteria": {"billing": "Charges", "technical": "Bugs"}},
         "refund": {"type": "noul", "instructions": "Is a refund requested?"}}
    state = "My invoice shows a duplicate charge, please refund it."
    line = "The invoice service retried the charge after a gateway timeout and billed the card twice. "
    over_window = line * 520           # about 47,000 characters, under the 50,000 cap, well over 8192 tokens
    over_chars = line * 600            # about 54,000 characters
    cases = [
        {"case": "startup", "seconds_to_health": startup_s},
        call("health, no key", path="/health", key=None),
        call("health, with key", path="/health"),
        call("success, no model (auto-route)", body={"state": state, "questions": Q}),
        call("success again (warm)", body={"state": state, "questions": Q}),
        call("over window (~47k chars)", body={"state": over_window, "questions": Q}),
        call("over 50,000 characters", body={"state": over_chars, "questions": Q}),
        call("bad model", body={"state": state, "questions": Q, "model": "nope/nothing"}),
        call("no key", body={"state": state, "questions": Q}, key=None),
    ]
    with ThreadPoolExecutor(24) as ex:
        burst = list(ex.map(lambda i: call(f"burst {i}", body={"state": over_window[:20000], "questions": Q}), range(24)))
    cases.append({"case": "burst of 24 concurrent", "statuses": sorted(c["status"] for c in burst if True),
                  "rejected_sample": next((c for c in burst if c["status"] == 503), None)})
finally:
    srv.terminate(); srv.wait(30)
json.dump(cases, open("results/capture_laya.json", "w"), indent=1)
for c in cases: print(json.dumps(c)[:700])
