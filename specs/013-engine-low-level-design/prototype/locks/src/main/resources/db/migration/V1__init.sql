create table run (
    id          uuid primary key,
    workflow_id text   not null,
    subject_key text   not null,
    status      text   not null,
    next_seq    int    not null default 1,
    version     bigint not null default 0
);
-- One active run per workflow and subject key.
create unique index one_active_run on run (workflow_id, subject_key) where status = 'active';

create table event (
    id      uuid primary key,
    run_id  uuid  not null references run (id),
    seq     int   not null,
    type    text  not null,
    payload jsonb not null,
    constraint event_run_seq unique (run_id, seq)
);

create table outbox (
    id            uuid primary key,
    run_id        uuid references run (id),
    kind          text  not null,
    payload       jsonb not null,
    due_at        timestamptz not null default now(),
    claimed_until timestamptz
);

create table deadline (
    id            uuid primary key,
    run_id        uuid not null references run (id),
    due_at        timestamptz not null,
    kind          text not null,
    -- Not in the brief's schema: added for test 4's "claim by timestamp" variant.
    claimed_until timestamptz
);

create table idempotency (
    key          text not null,
    principal    text not null,
    request_hash text not null,
    response     jsonb,
    created_at   timestamptz not null default now(),
    primary key (principal, key)
);
