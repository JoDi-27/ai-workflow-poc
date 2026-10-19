"""Spike 007: token size of each case packet (requirements + full diff), per tokenizer. No predictions."""
import glob, os
import laya
toks = {m: laya.load(f"convaiinnovations/{m}").tok
        for m in ["laya-typed-decisions", "laya-multilingual"]}
print(f"{'case':12} {'lines':>5} {'typed-dec':>9} {'multiling':>9}")
for p in sorted(glob.glob("cases/T*-*.patch")):
    t = os.path.basename(p)[:3]
    state = "Requirements:\n" + open(f"cases/{t}-requirements.md").read() + "\n\nDiff:\n" + open(p).read()
    n = {m: len(tk(state, add_special_tokens=False)["input_ids"]) for m, tk in toks.items()}
    print(f"{os.path.basename(p)[:-6]:12} {state.count(chr(10)):5} {n['laya-typed-decisions']:9} {n['laya-multilingual']:9}")
