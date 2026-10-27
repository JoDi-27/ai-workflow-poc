-- Engine schema, draft for Phase 1 (spike 013, docs/engine/design.md "Schema").
-- PostgreSQL 18. It becomes Flyway V1__engine.sql in the engine-app module.
-- Every key is an app-generated UUIDv7 (Hibernate @UuidGenerator(style = VERSION_7)).
-- Lifecycle columns are text with check constraints, matching the Java enums one to one.
-- Shown per core module, in dependency order. Analysis has its own schema and scripts.

create schema if not exists engine;
set search_path = engine;

-- ---------------------------------------------------------------- identity

create table workspace (
  id            uuid primary key,
  name          text not null unique
);

create table app_user (
  id            uuid primary key,
  workspace_id  uuid not null references workspace,
  handle        text not null unique,          -- shown in events and the UI
  display_name  text not null
);

create table role_assignment (
  user_id       uuid not null references app_user,
  role          text not null check (role in ('owner', 'approver', 'operator', 'admin')),
  primary key (user_id, role)
);

create table human_account (                  -- the engine's own login (AD-4)
  user_id       uuid primary key references app_user,
  password_hash text not null,                 -- Spring Security DelegatingPasswordEncoder format
  disabled      boolean not null default false
);

create table human_session (
  id            uuid primary key,
  user_id       uuid not null references app_user,
  token_hash    bytea not null unique,         -- SHA-256 of the cookie value
  created_at    timestamptz not null,
  expires_at    timestamptz not null
);

create table driver_token (                   -- one static token per user (spec 010 Q4)
  id            uuid primary key,
  user_id       uuid not null references app_user,
  token_hash    bytea not null unique,         -- SHA-256; the token itself is never stored
  created_at    timestamptz not null,
  revoked_at    timestamptz
);
create unique index one_live_driver_token on driver_token (user_id) where revoked_at is null;

-- ---------------------------------------------------------------- definitions

create table workflow (
  id            uuid primary key,
  workspace_id  uuid not null references workspace,
  key           text not null,                 -- the definition's `workflow`, e.g. sdlc-reference
  unique (workspace_id, key)
);

create table definition_version (
  id            uuid primary key,
  workflow_id   uuid not null references workflow,
  state         text not null check (state in ('draft', 'published', 'rejected', 'superseded')),
  number        integer,                       -- assigned on publish, sequential per workflow
  yaml          text not null,                 -- exactly as submitted and approved
  parsed        jsonb not null,                -- the parsed document the engine reads
  base_version  integer,                       -- submit_draft's base_version
  hypothesis    text,
  submitted_by  uuid not null references app_user,
  submitted_at  timestamptz not null,
  decided_by    uuid references app_user,      -- human approve or reject
  decided_at    timestamptz,
  unique (workflow_id, number),
  check ((state in ('published', 'superseded')) = (number is not null))
);
create unique index one_current_version on definition_version (workflow_id) where state = 'published';

-- ---------------------------------------------------------------- runs

create table run (                            -- aggregate root and lock row (Q4)
  id               uuid primary key,
  workflow_id      uuid not null references workflow,
  version_id       uuid not null references definition_version,  -- pinned
  subject_key      text not null,              -- canonical, opaque (spike 011 Q1)
  run_inputs       jsonb not null,
  owner_id         uuid not null references app_user,
  status           text not null check (status in ('active', 'completed', 'failed', 'abandoned')),
  current_visit_id uuid,                       -- fk added below
  open_attempt_id  uuid,                       -- fk added below; at most one open attempt
  next_event_seq   integer not null default 1, -- gapless per-run event order (Q9)
  started_at       timestamptz not null,
  ended_at         timestamptz
);
-- Partial index: an update of `status` under FOR NO KEY UPDATE does not need FOR UPDATE.
create unique index one_active_run on run (workflow_id, subject_key) where status = 'active';

create table stage_visit (
  id                        uuid primary key,
  run_id                    uuid not null references run,
  visit_no                  integer not null,  -- 1, 2, ... per run
  stage                     text not null,     -- stage ID from the definition
  entered_at                timestamptz not null,
  left_at                   timestamptz,
  task_state                text not null check (task_state in ('working', 'paused', 'exhausted', 'met', 'ended')),
  iterations                integer not null default 0,
  consecutive_failed_checks integer not null default 0,
  limits                    jsonb not null,    -- copied from the pinned version on entry
  unique (run_id, visit_no)
);
alter table run add foreign key (current_visit_id) references stage_visit;

create table iteration (
  id            uuid primary key,
  visit_id      uuid not null references stage_visit,
  n             integer not null,
  criteria_met  boolean not null,
  unmet         jsonb not null,                -- list of criterion texts
  reported_at   timestamptz not null,
  verdict       text not null check (verdict in ('continue', 'stop')),
  unique (visit_id, n)
);

create table escalation (
  id            uuid primary key,
  run_id        uuid not null references run,
  visit_id      uuid not null references stage_visit,
  reason        text not null,
  state         text not null check (state in ('open', 'extended', 'reworked', 'abandoned')),
  opened_at     timestamptz not null,
  resolved_by   uuid references app_user,
  resolved_at   timestamptz
);
create unique index one_open_escalation on escalation (run_id) where state = 'open';

create table driver_session (
  id             uuid primary key,             -- engine ID
  agent_session  text not null,                -- the agent's own session ID
  user_id        uuid not null references app_user,
  driver         jsonb not null,               -- provider, agent/kit/contract versions, model; recorded, never interpreted
  capabilities   text[] not null,
  bound_run_id   uuid references run,
  registered_at  timestamptz not null,
  unique (user_id, agent_session)
);

create table lease (
  id            uuid primary key,
  run_id        uuid not null references run,
  session_id    uuid not null references driver_session,
  user_id       uuid not null references app_user,
  acquired_at   timestamptz not null,
  released_at   timestamptz,
  released_why  text check (released_why in ('takeover', 'run_ended', 'rebind'))
);
create unique index one_active_lease on lease (run_id) where released_at is null;

create table evidence (                       -- driver-reported; specified only in the MVP (AD-27)
  id            uuid primary key,
  run_id        uuid not null references run,
  visit_id      uuid not null references stage_visit,
  type          text not null,
  revision      text not null,
  payload       jsonb not null,
  trust         text not null check (trust in ('driver_reported', 'engine_verified')),
  session_id    uuid references driver_session,
  submitted_at  timestamptz not null
);

create table event (                          -- append-only; the replay source (AD-5)
  id             uuid primary key,             -- deduplication key for analysis
  position       bigint generated always as identity unique,  -- global order for EventFeed paging
  run_id         uuid references run,          -- null for definition and identity events
  seq            integer,                      -- per-run, gapless, assigned under the run lock
  type           text not null,                -- e.g. run_started, attempt_opened, step_result_recorded
  audit_only     boolean not null default false,  -- denials (Q6): replay skips them
  actor_id       uuid references app_user,
  actor_type     text not null check (actor_type in ('driver', 'human', 'engine', 'runner', 'provider')),
  session_id     uuid references driver_session,
  version_id     uuid references definition_version,
  correlation_id text,
  occurred_at    timestamptz not null,
  payload        jsonb not null,               -- everything replay needs, incl. outcome-affecting settings
  unique (run_id, seq),
  check ((run_id is null) = (seq is null))
);

create table idempotency_record (             -- committed results only (Q5); purged after 7 days (Q13)
  principal     uuid not null,                 -- user ID (driver token or human session)
  key           text not null,
  operation     text not null,                 -- port operation name
  request_hash  bytea not null,                -- SHA-256 of the canonical request
  response      jsonb not null,                -- the port result record
  created_at    timestamptz not null,
  primary key (principal, key)
);
create index idempotency_expiry on idempotency_record (created_at);

create table telemetry_record (               -- joined to runs, not replayed
  id            uuid primary key,
  run_id        uuid not null references run,
  session_id    uuid not null references driver_session,
  name          text not null,                 -- neutral name from the collector
  observed_at   timestamptz not null,
  attributes    jsonb not null
);

-- ---------------------------------------------------------------- checkpoints

create table checkpoint_attempt (
  id                 uuid primary key,
  run_id             uuid not null references run,
  visit_id           uuid not null references stage_visit,
  transition         text not null,            -- transition ID from the definition
  attempt_no         integer not null,         -- per run and transition; echoed as expected_attempts
  revision           text not null,            -- opaque
  state              text not null check (state in ('running', 'passed', 'failed', 'cancelled', 'accepted', 'rejected')),
  current_step       integer not null default 0,  -- ordinal of the step being evaluated
  opened_at          timestamptz not null,
  closed_at          timestamptz,
  unique (run_id, transition, attempt_no)
);
alter table run add foreign key (open_attempt_id) references checkpoint_attempt;

create table step_result (
  id            uuid primary key,
  attempt_id    uuid not null references checkpoint_attempt,
  step          text not null,                 -- step ID
  ordinal       integer not null,
  role          text not null check (role in ('required', 'optional')),
  state         text not null check (state in ('pending', 'running', 'passed', 'failed', 'not_evaluated', 'overridden')),
  raw           jsonb,                         -- raw outcome kept after an override: median, member scores, exit, reason
  reason        text,                          -- one line, shown in status
  provenance    jsonb,                         -- runner, image, command, revision; executor kind
  settings      jsonb,                         -- outcome-affecting operational values (timeout, margin)
  decided_at    timestamptz,
  unique (attempt_id, ordinal),
  unique (attempt_id, step)
);

create table human_step_request (
  id             uuid primary key,
  step_result_id uuid not null unique references step_result,
  state          text not null check (state in ('waiting', 'approved', 'rejected', 'cancelled')),
  queued_at      timestamptz not null,
  decided_by     uuid references app_user,
  decided_at     timestamptz,
  comment        text
);

create table override (
  id             uuid primary key,
  step_result_id uuid not null unique references step_result,
  by_user        uuid not null references app_user,
  at             timestamptz not null,
  reason         text not null
);

-- ---------------------------------------------------------------- evaluation-tasks

create table evaluation_task (
  id             uuid primary key,
  step_result_id uuid not null references step_result,
  member_no      integer not null,             -- 1..council
  runs_in        text not null check (runs_in in ('runner', 'driver')),
  state          text not null check (state in ('issued', 'submitted', 'failed_to_run', 'cancelled')),
  packet         jsonb not null,               -- EvaluationTask packet (docs/driver-api)
  raw_score      numeric,
  band           text,
  rationale      text,
  usage          jsonb,                        -- evaluator modelUsage
  issued_at      timestamptz not null,
  closed_at      timestamptz,
  unique (step_result_id, member_no)
);

-- ---------------------------------------------------------------- outbox + deadlines

create table runner_job (
  id             uuid primary key,             -- also the Kubernetes Job name: "rj-" + lowercase UUID (39 chars)
  run_id         uuid not null references run,
  kind           text not null check (kind in ('command', 'evaluator', 'input_packet')),
  ref_id         uuid not null,                -- step_result, evaluation_task, or tree_walk
  state          text not null check (state in ('queued', 'dispatched', 'running', 'finished', 'cancelling', 'cancelled')),
  outcome        text check (outcome in ('passed', 'failed', 'not_evaluated')),
  packet         jsonb not null,
  result         jsonb,                        -- classification reason, provenance, usage, output, log tail
  deadline_at    timestamptz not null,         -- job timeout + margin (AD-6)
  next_poll_at   timestamptz,
  settings       jsonb not null,               -- timeout and margin used (replay)
  created_at     timestamptz not null,
  finished_at    timestamptz
);
create index runner_job_poll on runner_job (next_poll_at) where state in ('dispatched', 'running');

create table outbox_item (                    -- one queue for DueWork (Q8)
  id             uuid primary key,
  kind           text not null check (kind in ('dispatch_runner_job', 'cancel_runner_job', 'ask_question', 'publish_event', 'publish_telemetry')),
  run_id         uuid references run,          -- ordering key for publishing
  ref_id         uuid not null,                -- runner_job, decision_node, event, telemetry_record
  ordering       bigint,                       -- event position, for in-order publishing
  due_at         timestamptz not null,
  claimed_until  timestamptz,                  -- claim lease taken in DueWork phase 1
  claim_token    uuid,
  attempts       integer not null default 0,
  last_error     text,
  done_at        timestamptz
);
create index outbox_due on outbox_item (due_at) where done_at is null;

create table deadline (
  id            uuid primary key,
  run_id        uuid not null references run,
  kind          text not null check (kind in ('runner_job', 'decision_question')),
  ref_id        uuid not null,
  due_at        timestamptz not null,
  claimed_until timestamptz,                   -- claim lease taken in DueWork phase 1
  claim_token   uuid,
  fired_at      timestamptz,
  cleared_at    timestamptz                    -- the work finished first
);
create index deadline_due on deadline (due_at) where fired_at is null and cleared_at is null;

-- ---------------------------------------------------------------- typed-decisions

create table input_packet (                   -- stored once per tree (AD-29)
  id            uuid primary key,
  text          text not null,
  truncated     boolean not null,
  bytes         integer not null,
  revision      text not null
);

create table tree_walk (
  id               uuid primary key,
  run_id           uuid not null references run,
  step_result_id   uuid not null references step_result,
  root_question    text not null,
  provider         text not null,              -- from the definition (rule 12), opaque to the core
  mode             text not null check (mode in ('shadow')),
  state            text not null check (state in ('awaiting_packet', 'asking', 'concluded', 'ended_no_match', 'not_evaluated', 'cancelled')),
  input_packet_id  uuid references input_packet,
  conclusion       text,
  started_at       timestamptz not null,
  ended_at         timestamptz,
  unique (step_result_id, root_question, provider)
);

create table decision_node (                  -- one per question asked on the walk
  id             uuid primary key,
  walk_id        uuid not null references tree_walk,
  depth          integer not null,             -- 1..3 (rule 17)
  question       text not null,
  state          text not null check (state in ('asking', 'answered', 'not_evaluated')),
  label          text,
  confidence     numeric,
  distribution   jsonb,
  branch_taken   integer,                      -- index in `then`, null if none matched
  not_evaluated_reason text,                   -- provider_error, truncated, deadline
  asked_at       timestamptz not null,
  answered_at    timestamptz,
  unique (walk_id, depth)
);

create table provider_call (                  -- one row per call attempt (spike 008)
  id                    uuid primary key,
  node_id               uuid not null references decision_node,
  run_id                uuid not null references run,
  attempt_no            integer not null,
  gen_ai_provider_name  text not null,
  gen_ai_request_model  text not null,         -- pinned, never an alias
  gen_ai_response_model text,
  gen_ai_response_id    text,
  input_tokens          integer,
  output_tokens         integer,
  error_type            text,
  duration_ms           integer not null,
  provider_request_id   text,                  -- x-typesafe-request-id
  truncated             boolean,
  started_at            timestamptz not null
);

-- ---------------------------------------------------------------- scoring

create table run_score (                      -- recomputed on each pass and at the end; latest row wins
  id              uuid primary key,
  run_id          uuid not null references run,
  computed_at     timestamptz not null,
  earned          numeric not null,
  possible        numeric not null,
  deductions      jsonb not null,              -- line items: failed_attempt, rework_loop
  score           numeric not null,            -- unrounded, 0..100
  classification  text not null check (classification in ('provisional', 'scored', 'overridden', 'excluded')),
  breakdown       jsonb not null               -- per step: points, earned, latest evaluation
);
create index run_score_latest on run_score (run_id, computed_at desc);
