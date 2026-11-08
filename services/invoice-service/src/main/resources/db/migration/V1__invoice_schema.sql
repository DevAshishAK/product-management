create sequence invoice_number_seq start 1000;
create table invoice (
    id uuid primary key, version bigint not null default 0,
    created_at timestamptz not null, updated_at timestamptz not null, created_by varchar(64), updated_by varchar(64),
    invoice_number varchar(32) not null unique,
    order_id uuid not null unique,
    user_id uuid not null,
    total_amount numeric(19,2) not null,
    currency varchar(3) not null,
    status varchar(16) not null,
    issued_at timestamptz not null
);
create index idx_invoice_user on invoice (user_id);
create table invoice_line (
    invoice_id uuid not null references invoice (id),
    description varchar(200) not null,
    unit_price numeric(19,2) not null,
    quantity integer not null,
    line_total numeric(19,2) not null
);
