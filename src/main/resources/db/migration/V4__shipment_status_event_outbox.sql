-- Outbox transaccional de eventos de cambio de estado.
-- El payload se serializa una sola vez y se reutiliza en todos los reintentos.
CREATE TABLE shipment_status_event_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(36) NOT NULL,
    shipment_id VARCHAR(36) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    locked_at DATETIME(6) NULL,
    locked_by VARCHAR(100) NULL,
    sent_at DATETIME(6) NULL,

    CONSTRAINT pk_shipment_status_event_outbox PRIMARY KEY (id),
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id),

    INDEX idx_outbox_pending (status, next_attempt_at),
    INDEX idx_outbox_shipment_order (shipment_id, id)
);
