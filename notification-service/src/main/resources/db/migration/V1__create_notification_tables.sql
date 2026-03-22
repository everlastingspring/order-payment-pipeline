-- =============================================
-- Notification Service Schema
-- Tables: notifications, notification_log
-- =============================================

CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    customer_id     VARCHAR(255) NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    subject         VARCHAR(500) NOT NULL,
    body            TEXT,
    reference_id    VARCHAR(255),
    attempt_count   INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_log (
    id              UUID PRIMARY KEY,
    notification_id UUID REFERENCES notifications(id),
    event_type      VARCHAR(255) NOT NULL,
    event_id        VARCHAR(255),
    topic           VARCHAR(255) NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_notifications_customer    ON notifications(customer_id);
CREATE INDEX idx_notifications_type        ON notifications(type);
CREATE INDEX idx_notifications_status      ON notifications(status);
CREATE INDEX idx_notifications_reference   ON notifications(reference_id);
CREATE INDEX idx_notification_log_notif_id ON notification_log(notification_id);
CREATE INDEX idx_notification_log_event    ON notification_log(event_type);
