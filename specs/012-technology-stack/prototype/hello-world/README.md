# Spike 012 prototype: hello-world and definition parsing trial

Throwaway. It answers the spike's questions; it is not the engine. Findings are in `../../spec.md`.

Tried on 2026-10-21 with Java 25.0.3, Maven 3 (Homebrew), Spring Boot 4.1.1 (Tomcat 11.0.24, Jackson 3.1.5), MCP Java SDK 2.1.0, networknt json-schema-validator 3.0.8, and Claude Code 2.1.303.

## Parts

| Module | What it is |
| --- | --- |
| `engine/` | Stub engine on Spring Boot: `GET /v1/whoami` and `GET /v1/status?subject=` with a bearer driver token (`engine.driver-token`, default `dev-token`). Port 18787. |
| `mcp-gateway/` | MCP gateway on Spring Boot, hosting the SDK's stateful `HttpServletStreamableServerTransportProvider` as a servlet at `/mcp` (no Spring AI). A filter checks the caller's token with the engine's `/v1/whoami` on every MCP request; the `status` tool forwards the token unchanged to `/v1/status`. Port 18788. |
| `definition-trial/` | Parses `docs/definitions/sdlc-reference.yaml` with Jackson 3 YAML (duplicate detection on) and validates it against `partial-schema.json` (draft 2020-12, a partial structure schema for the trial only), mapping each error's JSON pointer to a YAML line. |

## Rerun

```sh
mvn -q -B -DskipTests package
java -jar engine/target/engine-0.jar &
java -jar mcp-gateway/target/mcp-gateway-0.jar &
printf '{"mcpServers":{"engine":{"type":"http","url":"http://localhost:18788/mcp","headers":{"Authorization":"Bearer dev-token"}}}}' > /tmp/mcp.json
claude -p --strict-mcp-config --mcp-config /tmp/mcp.json --allowedTools mcp__engine__status --model haiku \
  "Call the engine status tool with subject 'repo-a/main' and print its raw output."
java -cp "definition-trial/target/classes:$(cd definition-trial && mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" \
  hello.definitions.Trial ../../../../docs/definitions/sdlc-reference.yaml
```

Ports 18787 and 18788 were used because another local process held 127.0.0.1:8787.

## Container trials (Docker Desktop 29.5.2, arm64)

| Module | What it shows |
| --- | --- |
| `jpa-trial/` | Spring Boot 4.1.1, Hibernate 7.4.5, Flyway 12.4.0, Testcontainers 2.0.5 with `postgres:18-alpine`: Flyway migration plus `ddl-auto=validate` on a `jsonb` column; `jsonb` round trip and a native `->>` query; the partial unique index "one active run" raising `DataIntegrityViolationException` naming `one_active_run`; the run-row lock blocking a second transaction (`NOWAIT` fails at once); the outbox claim with `SKIP LOCKED` giving two transactions disjoint rows. |
| `pubsub-trial/` | `google-cloud-pubsub` (libraries-bom 26.90.0) against the Pub/Sub emulator (`google-cloud-cli:emulators`) through Testcontainers' `PubSubEmulatorContainer`: per-run order with the run ID as ordering key; a nack redelivers the key's messages in order; dead-lettering after 5 attempts on ordered and unordered subscriptions. |

```sh
mvn -B test -pl jpa-trial,pubsub-trial
```
