create table category (
    id uuid primary key, version bigint not null default 0,
    created_at timestamptz not null, updated_at timestamptz not null, created_by varchar(64), updated_by varchar(64),
    name varchar(100) not null unique,
    description varchar(500)
);
create table product (
    id uuid primary key, version bigint not null default 0,
    created_at timestamptz not null, updated_at timestamptz not null, created_by varchar(64), updated_by varchar(64),
    sku varchar(64) not null unique,
    name varchar(200) not null,
    description text,
    price numeric(19,2) not null,
    currency varchar(3) not null,
    active boolean not null default true,
    category_id uuid references category (id)
);
create index idx_product_category on product (category_id);
create index idx_product_name on product (lower(name));
