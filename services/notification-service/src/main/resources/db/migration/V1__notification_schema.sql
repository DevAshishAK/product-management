create table notification (
    id uuid primary key, version bigint not null default 0,
    created_at timestamptz not null, updated_at timestamptz not null, created_by varchar(64), updated_by varchar(64),
    user_id uuid not null,
    channel varchar(16) not null,
    template varchar(64) not null,
    subject varchar(200) not null,
    body text not null,
    status varchar(16) not null,
    error varchar(500)
);
create index idx_notification_user on notification (user_id, created_at desc);
