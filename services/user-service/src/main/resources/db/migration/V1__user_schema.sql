create table role (
    id uuid primary key, version bigint not null default 0,
    created_at timestamptz not null, updated_at timestamptz not null, created_by varchar(64), updated_by varchar(64),
    name varchar(64) not null unique
);
create table role_permission (
    role_id uuid not null references role (id),
    permission varchar(64) not null,
    primary key (role_id, permission)
);
create table user_account (
    id uuid primary key, version bigint not null default 0,
    created_at timestamptz not null, updated_at timestamptz not null, created_by varchar(64), updated_by varchar(64),
    email varchar(255) not null unique,
    password_hash varchar(100) not null,
    full_name varchar(200) not null,
    phone varchar(32),
    active boolean not null default true
);
create table user_role (
    user_id uuid not null references user_account (id),
    role_id uuid not null references role (id),
    primary key (user_id, role_id)
);

insert into role (id, version, created_at, updated_at, name) values
 (gen_random_uuid(), 0, now(), now(), 'ADMIN'),
 (gen_random_uuid(), 0, now(), now(), 'CUSTOMER'),
 (gen_random_uuid(), 0, now(), now(), 'INVENTORY_MANAGER'),
 (gen_random_uuid(), 0, now(), now(), 'SUPPORT');

insert into role_permission (role_id, permission)
select r.id, p from role r, unnest(array['product:read','product:write','order:read','order:write','order:cancel',
  'inventory:read','inventory:write','payment:read','payment:write','invoice:read','user:read','user:write',
  'audit:read','notification:read']) p where r.name = 'ADMIN';
insert into role_permission (role_id, permission)
select r.id, p from role r, unnest(array['product:read','order:read','order:write','order:cancel','payment:read',
  'invoice:read','notification:read']) p where r.name = 'CUSTOMER';
insert into role_permission (role_id, permission)
select r.id, p from role r, unnest(array['product:read','inventory:read','inventory:write']) p where r.name = 'INVENTORY_MANAGER';
insert into role_permission (role_id, permission)
select r.id, p from role r, unnest(array['product:read','order:read','payment:read','invoice:read','user:read','notification:read']) p where r.name = 'SUPPORT';
