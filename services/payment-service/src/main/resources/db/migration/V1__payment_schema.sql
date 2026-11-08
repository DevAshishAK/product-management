create table payment (
    id uuid primary key, version bigint not null default 0,
    created_at timestamptz not null, updated_at timestamptz not null, created_by varchar(64), updated_by varchar(64),
    order_id uuid not null unique,
    user_id uuid not null,
    amount numeric(19,2) not null,
    currency varchar(3) not null,
    status varchar(24) not null,
    gateway_ref varchar(100),
    failure_reason varchar(500),
    refunded_amount numeric(19,2) not null default 0
);
create index idx_payment_user on payment (user_id);
