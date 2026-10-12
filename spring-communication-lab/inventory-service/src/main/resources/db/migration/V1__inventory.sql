create table products (sku varchar(50) primary key, name varchar(120) not null,
 price numeric(12,2) not null check(price>0), currency varchar(3) not null);
create table sales_receipts (event_id uuid primary key, order_id uuid not null unique,
 payment_id uuid not null unique, sku varchar(50) not null, quantity integer not null check(quantity>0), received_at timestamptz not null);
-- SQL is used only for schema/seed migrations. Runtime application DML is HQL.
insert into products values ('BOOK-1','Spring Boot practice book',500.00,'INR');
insert into products values ('PEN-1','Notebook pen',25.00,'INR');
