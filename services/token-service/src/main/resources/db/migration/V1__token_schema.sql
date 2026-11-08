create table oauth_client (
    client_id     varchar(100) primary key,
    secret_hash   varchar(100),
    public_client boolean       not null default false,
    roles         varchar(500)  not null default '',
    permissions   varchar(1000) not null default '',
    grant_types   varchar(200)  not null,
    active        boolean       not null default true,
    created_at    timestamptz   not null
);

create table refresh_token (
    id         uuid primary key,
    token_hash varchar(64)  not null unique,
    subject    varchar(100) not null,
    client_id  varchar(100) not null,
    expires_at timestamptz  not null,
    revoked    boolean      not null default false,
    created_at timestamptz  not null
);
create index idx_refresh_subject on refresh_token (subject);

create table revoked_token (
    jti        varchar(64) primary key,
    expires_at timestamptz not null
);
