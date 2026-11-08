create table orders (
    id uuid primary key, version bigint not null default 0,
    created_at timestamptz not null, updated_at timestamptz not null, created_by varchar(64), updated_by varchar(64),
    user_id uuid not null,
    status varchar(32) not null,
    total_amount numeric(19,2) not null,
    currency varchar(3) not null,
    cancel_reason varchar(500)
);
create index idx_orders_user_created on orders (user_id, created_at desc);

create table order_item (
    order_id uuid not null references orders (id),
    product_id uuid not null,
    product_name varchar(200) not null,
    unit_price numeric(19,2) not null,
    quantity integer not null
);
create index idx_order_item_order on order_item (order_id);

create table order_status_history (
    id uuid primary key,
    order_id uuid not null,
    status varchar(32) not null,
    reason varchar(500),
    changed_at timestamptz not null
);
create index idx_order_history_order on order_status_history (order_id, changed_at);
