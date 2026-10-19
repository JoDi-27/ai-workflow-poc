"""Throwaway analyzer for spike 006: flattens a capture from receiver.py and prints
what each session sent. Run: python3 analyze.py <work-dir>
Content attributes (prompts, responses, tool input) are printed only as lengths."""
import collections
import json
import sys
from pathlib import Path

WORK = Path(sys.argv[1])
CAP = WORK / "capture"
CONTENT = {"prompt", "prompt_text", "response", "response_text", "tool_input", "tool_parameters",
           "error", "body", "user.email", "user.account_uuid", "organization.id", "user.id"}


def val(v):
    for k in ("stringValue", "intValue", "doubleValue", "boolValue"):
        if k in v:
            return v[k]
    if "arrayValue" in v:
        return [val(x) for x in v["arrayValue"].get("values", [])]
    return v


def attrs(lst):
    return {a["key"]: val(a["value"]) for a in lst or []}


def show(d):
    return {k: (f"<{len(str(v))} chars>" if k in CONTENT else v) for k, v in d.items()}


def records(signal):
    path = CAP / f"{signal}.jsonl"
    if not path.exists():
        return
    for line in path.open():
        rec = json.loads(line)
        for res in rec["body"].get({"logs": "resourceLogs", "metrics": "resourceMetrics", "traces": "resourceSpans"}[signal], []):
            r = attrs(res.get("resource", {}).get("attributes"))
            scopes = res.get("scopeLogs") or res.get("scopeMetrics") or res.get("scopeSpans") or []
            for sc in scopes:
                yield rec, r, sc


sessions = dict(kv.split("=") for kv in (WORK / "sessions.txt").read_text().split())
name_of = {v: k for k, v in sessions.items()}
print("sessions:", sessions)

print("\n# resource attributes and auth per signal")
seen = set()
for signal in ("logs", "metrics", "traces"):
    for rec, r, sc in records(signal):
        key = (signal, json.dumps(show(r), sort_keys=True), rec["auth"], sc.get("scope", {}).get("name"))
        if key not in seen:
            seen.add(key)
            print(signal, "auth:", rec["auth"], "scope:", key[3], "resource:", key[1])

print("\n# events per session")
events = collections.defaultdict(list)
for rec, r, sc in records("logs"):
    for lr in sc.get("logRecords", []):
        a = attrs(lr.get("attributes"))
        events[a.get("session.id")].append(a)
for sid, evs in events.items():
    print(f"\n## session {name_of.get(sid, sid)}: {collections.Counter(e.get('event.name') for e in evs)}")
    shown = set()
    for e in sorted(evs, key=lambda e: (e.get("event.timestamp", ""), e.get("event.sequence", 0))):
        n = e.get("event.name")
        if n in ("api_request", "tool_result", "tool_decision", "subagent_completed", "skill_activated",
                 "user_prompt", "assistant_response", "hook_execution_complete") or n not in shown:
            print(" ", show(e))
        shown.add(n)

print("\n# metrics: name -> datapoint attribute keys and sums per session")
msum = collections.defaultdict(float)
mkeys = collections.defaultdict(set)
for rec, r, sc in records("metrics"):
    for m in sc.get("metrics", []):
        body = m.get("sum") or m.get("gauge") or {}
        for dp in body.get("dataPoints", []):
            a = attrs(dp.get("attributes"))
            mkeys[m["name"]] |= set(a)
            v = dp.get("asDouble", dp.get("asInt", 0))
            label = a.get("type") or a.get("model") or ""
            msum[(m["name"], name_of.get(a.get("session.id"), a.get("session.id")), label,
                  a.get("query_source"), a.get("agent.name"), a.get("skill.name"))] += float(v)
for k, keys in mkeys.items():
    print(k, sorted(keys))
for k, v in sorted(msum.items(), key=lambda kv: str(kv[0])):
    print(" ", k, round(v, 6))

print("\n# spans per session")
spans = collections.defaultdict(list)
for rec, r, sc in records("traces"):
    for sp in sc.get("spans", []):
        a = attrs(sp.get("attributes"))
        spans[a.get("session.id")].append((sp["name"], show(a)))
for sid, sps in spans.items():
    print(f"\n## session {name_of.get(sid, sid)}: {collections.Counter(n for n, _ in sps)}")
    shown = set()
    for n, a in sps:
        if n not in shown:
            print(" ", n, a)
            shown.add(n)

print("\n# hooks: event, input session_id, env session id")
text, pos, dec = (WORK / "hooks.jsonl").read_text(), 0, json.JSONDecoder()
while True:
    while pos < len(text) and text[pos].isspace():
        pos += 1
    if pos >= len(text):
        break
    h, pos = dec.raw_decode(text, pos)
    i = h["input"]
    print(" ", i.get("hook_event_name"), name_of.get(i.get("session_id"), i.get("session_id")),
          "env:", name_of.get(h["hook_env_session_id"], h["hook_env_session_id"]),
          i.get("source", ""), i.get("tool_name", ""), i.get("tool_use_id", ""), i.get("duration_ms", ""))

print("\n# -p json results")
for f in sorted(WORK.glob("*.result.json")):
    j = json.loads(f.read_text())
    print(" ", f.name, "session:", name_of.get(j.get("session_id"), j.get("session_id")),
          "cost:", j.get("total_cost_usd"), "usage:", j.get("usage"), "modelUsage:", j.get("modelUsage"))
