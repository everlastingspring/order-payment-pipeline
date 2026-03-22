-- =============================================
-- Payment Service Schema
-- Tables: payments, idempotency_keys, outbox_events
-- =============================================

CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    order_id        VARCHAR(255) NOT NULL,
    customer_id     VARCHAR(255) NOT NULL,
    status          VARCHAR(50)    NOT NULL,
    amount          DECIMAL(19,2)  NOT NULL,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'INR',
    payment_method  VARCHAR(50)    NOT NULL,
    idempotency_key VARCHAR(255),
    transaction_reference VARCHAR(255),
    failure_reason  TEXT,
    failure_code    VARCHAR(100),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE idempotency_keys (
    id              UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    payment_id      UUID         NOT NULL REFERENCES payments(id),
    response_status VARCHAR(50)  NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(255) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(255) NOT NULL,
    topic           VARCHAR(255) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP WITH TIME ZONE,
    retry_count     INT          NOT NULL DEFAULT 0
);

-- Indexes
CREATE INDEX idx_payments_order_id        ON payments(order_id);
CREATE INDEX idx_payments_customer_id     ON payments(customer_id);
CREATE INDEX idx_payments_status          ON payments(status);
CREATE INDEX idx_payments_idempotency_key ON payments(idempotency_key);
CREATE INDEX idx_idempotency_keys_key     ON idempotency_keys(idempotency_key);
CREATE INDEX idx_idempotency_keys_expires ON idempotency_keys(expires_at);
CREATE INDEX idx_outbox_events_status     ON outbox_events(status);
CREATE INDEX idx_outbox_events_status_created ON outbox_events(status, created_at);
