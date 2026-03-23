-- =============================================
-- Reservation records: tracks per-order inventory reservations
-- Used for saga compensation (inventory release on order cancel)
-- =============================================

CREATE TABLE reservation_records (
    id              UUID PRIMARY KEY,
    order_id        VARCHAR(255) NOT NULL,
    product_id      UUID         NOT NULL REFERENCES products(id),
    warehouse_id    VARCHAR(255) NOT NULL DEFAULT 'WH-001',
    quantity        INT          NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    reserved_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    released_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_reservation_order_id ON reservation_records(order_id);
CREATE INDEX idx_reservation_status   ON reservation_records(status);
CREATE INDEX idx_reservation_order_status ON reservation_records(order_id, status);
