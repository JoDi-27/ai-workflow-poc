create table run (
    id          uuid primary key,
    workflow    text not null,
    subject_key text not null,
    status      text not null,
    state       jsonb not null
);
-- One active run per workflow and subject key (spec 010, AD-16).
create unique index one_active_run on run (workflow, subject_key) where status = 'active';

create table outbox (
    id           bigserial primary key,
    run_id       uuid not null references run (id),
    payload      jsonb not null,
    published_at timestamptz
);
