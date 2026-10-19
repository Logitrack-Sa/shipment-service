
-- V1__init_schema.sql
-- Esquema inicial de LogiTrack para MySQL

-- 1. Clientes
CREATE TABLE customers (
                           id BINARY(16) NOT NULL,
                           name VARCHAR(100) NOT NULL,
                           email VARCHAR(254) NOT NULL,
                           phone VARCHAR(30) NOT NULL,

                           created_at DATETIME(6) NOT NULL,
                           updated_at DATETIME(6) NOT NULL,
                           created_by VARCHAR(255) NOT NULL,
                           last_modified_by VARCHAR(255) NOT NULL,
                           version BIGINT NOT NULL,

                           CONSTRAINT pk_customers PRIMARY KEY (id),
                           CONSTRAINT uk_customers_email UNIQUE (email)
);

-- 2. Repartidores
CREATE TABLE couriers (
                          id BINARY(16) NOT NULL,
                          name VARCHAR(120) NOT NULL,
                          vehicle_type VARCHAR(50) NOT NULL,
                          status VARCHAR(20) NOT NULL,

                          created_at DATETIME(6) NOT NULL,
                          updated_at DATETIME(6) NOT NULL,
                          created_by VARCHAR(255) NOT NULL,
                          last_modified_by VARCHAR(255) NOT NULL,
                          version BIGINT NOT NULL,

                          CONSTRAINT pk_couriers PRIMARY KEY (id)
);

-- 3. Envíos
CREATE TABLE shipments (
                           id BINARY(16) NOT NULL,
                           tracking_code VARCHAR(13) NOT NULL,
                           customer_id BINARY(16) NOT NULL,
                           courier_id BINARY(16) NULL,

                           origin_street VARCHAR(150) NOT NULL,
                           origin_number VARCHAR(20) NOT NULL,
                           origin_city VARCHAR(100) NOT NULL,
                           origin_postal_code VARCHAR(20) NOT NULL,

                           destination_street VARCHAR(150) NOT NULL,
                           destination_number VARCHAR(20) NOT NULL,
                           destination_city VARCHAR(100) NOT NULL,
                           destination_postal_code VARCHAR(20) NOT NULL,

                           weight_kg DECIMAL(12,3) NOT NULL,
                           width_cm DECIMAL(10,2) NOT NULL,
                           height_cm DECIMAL(10,2) NOT NULL,
                           length_cm DECIMAL(10,2) NOT NULL,
                           fee DECIMAL(12,2) NOT NULL,
                           status VARCHAR(20) NOT NULL,

                           created_at DATETIME(6) NOT NULL,
                           updated_at DATETIME(6) NOT NULL,
                           created_by VARCHAR(255) NOT NULL,
                           last_modified_by VARCHAR(255) NOT NULL,
                           version BIGINT NOT NULL,

                           CONSTRAINT pk_shipments PRIMARY KEY (id),

                           CONSTRAINT uk_shipments_tracking_code
                               UNIQUE (tracking_code),

                           CONSTRAINT fk_shipments_customer
                               FOREIGN KEY (customer_id)
                                   REFERENCES customers(id),

                           CONSTRAINT fk_shipments_courier
                               FOREIGN KEY (courier_id)
                                   REFERENCES couriers(id),

                           CONSTRAINT chk_shipments_weight
                               CHECK (weight_kg > 0),

                           CONSTRAINT chk_shipments_width
                               CHECK (width_cm > 0),

                           CONSTRAINT chk_shipments_height
                               CHECK (height_cm > 0),

                           CONSTRAINT chk_shipments_length
                               CHECK (length_cm > 0),

                           CONSTRAINT chk_shipments_fee
                               CHECK (fee >= 0),

                           INDEX idx_shipments_status (status)
);

-- 4. Historial de estados
CREATE TABLE shipment_status_history (
                                         id BINARY(16) NOT NULL,
                                         shipment_id BINARY(16) NOT NULL,
                                         previous_status VARCHAR(20) NULL,
                                         new_status VARCHAR(20) NOT NULL,
                                         changed_at DATETIME(6) NOT NULL,
                                         observation VARCHAR(255) NULL,

                                         created_at DATETIME(6) NOT NULL,
                                         updated_at DATETIME(6) NOT NULL,
                                         created_by VARCHAR(255) NOT NULL,
                                         last_modified_by VARCHAR(255) NOT NULL,
                                         version BIGINT NOT NULL,

                                         CONSTRAINT pk_shipment_status_history PRIMARY KEY (id),

                                         CONSTRAINT fk_status_history_shipment
                                             FOREIGN KEY (shipment_id)
                                                 REFERENCES shipments(id)
);
