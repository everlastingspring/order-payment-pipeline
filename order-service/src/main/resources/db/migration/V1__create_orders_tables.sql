-- Order aggregate root
CREATE TABLE orders (
    id                  UUID PRIMARY KEY,
    customer_id         VARCHAR(255) NOT NULL,
    customer_email      VARCHAR(255) NOT NULL,
    status              VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    saga_status         VARCHAR(50)  NOT NULL DEFAULT 'STARTED',
    total_amount        DECIMAL(19,2) NOT NULL,
    currency            VARCHAR(3)   NOT NULL DEFAULT 'INR',
    payment_method      VARCHAR(50)  NOT NULL,
    shipping_address    TEXT,
    payment_id          VARCHAR(255),
    payment_completed   BOOLEAN NOT NULL DEFAULT FALSE,
    inventory_reserved  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Line items within an order
CREATE TABLE order_items (
    id              UUID PRIMARY KEY,
    order_id        UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      VARCHAR(255) NOT NULL,
    product_name    VARCHAR(255),
    sku             VARCHAR(255),
    quantity        INT NOT NULL,
    unit_price      DECIMAL(19,2) NOT NULL,
    currency        VARCHAR(3)   NOT NULL DEFAULT 'INR',
    subtotal        DECIMAL(19,2) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Transactional outbox for reliable Kafka publishing
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(255) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(255) NOT NULL,
    topic           VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP WITH TIME ZONE,
    retry_count     INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_customer_id         ON orders(customer_id);
CREATE INDEX idx_orders_status              ON orders(status);
CREATE INDEX idx_order_items_order_id       ON order_items(order_id);
CREATE INDEX idx_outbox_status_created      ON outbox_events(status, created_at);
