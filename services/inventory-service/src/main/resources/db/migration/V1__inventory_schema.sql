create table inventory_item (
    id uuid primary key, version bigint not null default 0,
    created_at timestamptz not null, updated_at timestamptz not null, created_by varchar(64), updated_by varchar(64),
    product_id uuid not null unique,
    available integer not null check (available >= 0),
    reserved integer not null check (reserved >= 0)
);
create table reservation (
    id uuid primary key, version bigint not null default 0,
    created_at timestamptz not null, updated_at timestamptz not null, created_by varchar(64), updated_by varchar(64),
    order_id uuid not null unique,
    status varchar(16) not null
);
create table reservation_line (
    reservation_id uuid not null references reservation (id),
    product_id uuid not null,
    quantity integer not null
);
create index idx_reservation_line_res on reservation_line (reservation_id);
