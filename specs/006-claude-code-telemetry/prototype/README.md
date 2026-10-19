# Spike 006 prototype

Throwaway. Captures what Claude Code's OpenTelemetry export sends, without a collector.

```sh
python3 receiver.py <work>/capture &   # OTLP/HTTP JSON on 127.0.0.1:4318
./capture.sh <work>                     # five short headless sessions (haiku)
python3 analyze.py <work>               # per-session events, metrics, spans, hooks, -p results
```

`capture.sh` starts each session with a clean env (as a plain terminal would), sets the telemetry
variables only on that process, and logs hook input to `<work>/hooks.jsonl`. Raw captures hold the
account's email and IDs, so keep them out of the repository; the results are in `../spec.md`.
