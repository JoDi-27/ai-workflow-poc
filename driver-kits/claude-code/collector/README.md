# collector

The OTel Collector pipeline configuration. This folder is the one place for it: the configuration is kit-owned, because it deletes and redacts Claude Code attributes and maps Claude Code names to neutral ones.

Until the item below lands, the draft configuration from spike 014 still sits inline in `deploy/base/collector.yaml`. That item moves it here and wires it into the deployment.

Read: `docs/architecture/README.md` (Components), then `specs/006-claude-code-telemetry/` for the attributes and the redaction.

Filled by: PLAN Phase 0 / Scaffolding / OTel Collector
