-- Shared infrastructure tables, applied to every JPA service database (outbox, inbox/dedup, idempotency).
create table outbox_event (
    id           uuid primary key,
    aggregate_id varchar(64)  not null,
    topic        varchar(128) not null,
    event_key    varchar(128) not null,
    event_type   varchar(64)  not null,
    payload      text         not null,
    published    boolean      not null default false,
    created_at   timestamptz  not null,
    published_at timestamptz
);
create index idx_outbox_unpublished on outbox_event (created_at) where published = false;

create table processed_event (
    id           varchar(200) primary key,
    processed_at timestamptz not null
);

create table idempotency_record (
    id              uuid primary key,
    scope           varchar(64)  not null,
    idempotency_key varchar(160) not null,
    request_hash    varchar(64)  not null,
    response_body   text         not null,
    created_at      timestamptz  not null,
    constraint uq_idempotency unique (scope, idempotency_key)
);
