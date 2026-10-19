"""Spike 007 probe: truncation reporting, max_len limits, and latency per checkpoint on this machine."""
import time, sys, laya, torch

Q = {"code_quality_band": {"type": "choice",
     "instructions": "How good is the code change in the state, judged against its requirements?",
     "criteria": {"poor": "Wrong, unsafe, or untested; major problems.",
                  "weak": "Works for the main case but has gaps or risks.",
                  "good": "Correct, tested, and clear, with minor issues at most.",
                  "exemplary": "Correct, thoroughly tested, and clean throughout."}}}
line = "+    return contactRepository.countByCompanyIgnoreCase(company.trim());\n"
print("device mps:", torch.backends.mps.is_available())
for model, max_len in [("convaiinnovations/laya", None), ("convaiinnovations/laya-typed-decisions", None),
                       ("convaiinnovations/laya-multilingual", None), ("convaiinnovations/laya-multilingual", 8192)]:
    t = time.time(); a = laya.load(model); load_s = time.time() - t
    for n in (40, 400):
        state = "Requirements: add GET /contacts/count.\n\ndiff --git a/X.java b/X.java\n" + line * n
        t = time.time(); r = a.predict(state, Q, max_len=max_len); cold = time.time() - t
        t = time.time(); r = a.predict(state, Q, max_len=max_len); warm = time.time() - t
        u = r.get("usage", {})
        print(f"{model.split('/')[1]:22} max_len={max_len} lines={n:3} load={load_s:5.1f}s cold={cold:6.2f}s warm={warm:6.3f}s "
              f"answer={r['answers']['code_quality_band'] if 'answers' in r else r} truncated={u.get('truncated')} "
              f"state_tokens={u.get('state_tokens')} dropped={u.get('state_tokens_dropped')}")
    del a
