ALTER TABLE customers ADD COLUMN owner_user_id BINARY(16) NULL;
ALTER TABLE customers ADD CONSTRAINT uk_customer_owner UNIQUE (owner_user_id);
ALTER TABLE customers MODIFY name VARCHAR(200) NOT NULL;
ALTER TABLE customers MODIFY phone VARCHAR(30) NULL;
