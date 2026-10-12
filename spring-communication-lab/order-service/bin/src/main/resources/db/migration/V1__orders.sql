create table purchase_orders (id uuid primary key,sku varchar(50) not null,quantity integer not null check(quantity>0),
 amount numeric(12,2) not null check(amount>0),currency varchar(3) not null,note varchar(200) not null,
 status varchar(20) not null check(status in ('DRAFT','PENDING','PAID')),payment_id uuid,created_at timestamptz not null);
create index orders_status_created_idx on purchase_orders(status,created_at);
create table processed_events (event_id uuid primary key,processed_at timestamptz not null);
