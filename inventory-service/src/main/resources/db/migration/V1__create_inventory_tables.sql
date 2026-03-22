-- =============================================
-- Inventory Service Schema
-- Tables: products, inventory, outbox_events
-- =============================================

CREATE TABLE products (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    sku         VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    price       DECIMAL(19,2) NOT NULL,
    currency    VARCHAR(3)    NOT NULL DEFAULT 'INR',
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE inventory (
    id                  UUID PRIMARY KEY,
    product_id          UUID    NOT NULL REFERENCES products(id),
    available_quantity  INT     NOT NULL DEFAULT 0,
    reserved_quantity   INT     NOT NULL DEFAULT 0,
    warehouse_id        VARCHAR(255) NOT NULL DEFAULT 'WH-001',
    status              VARCHAR(50)  NOT NULL DEFAULT 'AVAILABLE',
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, warehouse_id)
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
CREATE INDEX idx_products_sku            ON products(sku);
CREATE INDEX idx_products_active         ON products(active);
CREATE INDEX idx_inventory_product_id    ON inventory(product_id);
CREATE INDEX idx_inventory_warehouse     ON inventory(warehouse_id);
CREATE INDEX idx_inventory_status        ON inventory(status);
CREATE INDEX idx_outbox_events_status    ON outbox_events(status);
CREATE INDEX idx_outbox_events_status_created ON outbox_events(status, created_at);

-- Seed data: sample products with inventory
INSERT INTO products (id, name, sku, description, price, currency) VALUES
    ('a1b2c3d4-0000-0000-0000-000000000001', 'Wireless Mouse',      'MOUSE-WL-001', 'Ergonomic wireless mouse',        799.00,  'INR'),
    ('a1b2c3d4-0000-0000-0000-000000000002', 'Mechanical Keyboard',  'KB-MECH-001',  'RGB mechanical keyboard',         2499.00, 'INR'),
    ('a1b2c3d4-0000-0000-0000-000000000003', 'USB-C Hub',            'HUB-USBC-001', '7-in-1 USB-C hub',                1299.00, 'INR'),
    ('a1b2c3d4-0000-0000-0000-000000000004', 'Laptop Stand',         'STAND-LP-001', 'Adjustable aluminium laptop stand', 999.00, 'INR'),
    ('a1b2c3d4-0000-0000-0000-000000000005', 'Webcam HD',            'CAM-HD-001',   '1080p HD webcam with mic',        1899.00, 'INR');

INSERT INTO inventory (id, product_id, available_quantity, reserved_quantity, warehouse_id, status) VALUES
    (gen_random_uuid(), 'a1b2c3d4-0000-0000-0000-000000000001', 100, 0, 'WH-001', 'AVAILABLE'),
    (gen_random_uuid(), 'a1b2c3d4-0000-0000-0000-000000000002',  50, 0, 'WH-001', 'AVAILABLE'),
    (gen_random_uuid(), 'a1b2c3d4-0000-0000-0000-000000000003',  75, 0, 'WH-001', 'AVAILABLE'),
    (gen_random_uuid(), 'a1b2c3d4-0000-0000-0000-000000000004',  30, 0, 'WH-001', 'AVAILABLE'),
    (gen_random_uuid(), 'a1b2c3d4-0000-0000-0000-000000000005',   0, 0, 'WH-001', 'OUT_OF_STOCK');
