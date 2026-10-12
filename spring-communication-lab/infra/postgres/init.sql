-- Separate owners and databases: services cannot query one another's tables.
create user "order" with password 'order-password';
create user payment with password 'payment-password';
create user inventory with password 'inventory-password';
create database orders owner "order";
create database payments owner payment;
create database inventory owner inventory;
