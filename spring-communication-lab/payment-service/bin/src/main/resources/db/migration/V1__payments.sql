create table payments (id uuid primary key, idempotency_key varchar(100) not null unique,
 order_id uuid not null unique, sku varchar(50) not null, quantity integer not null check(quantity>0),
 amount numeric(12,2) not null check(amount>0), currency varchar(3) not null, status varchar(20) not null, created_at timestamptz not null);
create table outbox_events (id uuid primary key,payload text not null,traceparent varchar(100),
 correlation_id varchar(100) not null,sent boolean not null default false,attempts integer not null default 0,created_at timestamptz not null);
create index outbox_pending_idx on outbox_events(sent,created_at);
