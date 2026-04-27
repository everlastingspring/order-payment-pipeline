package com.paymentplatform.notificationservice.domain.repository;

import com.paymentplatform.notificationservice.domain.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link NotificationLog} entities.
 *
 * <p>The notification log is an append-only audit trail. A new log row is written for each
 * send attempt regardless of outcome. Rows are never updated or deleted, so all queries
 * are read-only from the domain perspective.</p>
 */
@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    /**
     * Returns the full attempt history for a given notification, newest first.
     * Used to display retry history and determine why a notification is in its current state.
     *
     * @param notificationId the parent notification identifier
     * @return list of log entries ordered by {@code createdAt DESC}
     */
    List<NotificationLog> findByNotificationIdOrderByCreatedAtDesc(UUID notificationId);

    /**
     * Returns all log entries for a given Kafka event type
     * (e.g. {@code "order.created"}, {@code "payment.completed"}).
     * Useful for tracing how many send attempts were made across all notifications
     * triggered by a specific event type.
     *
     * @param eventType the Kafka topic/event type string
     * @return list of matching log entries
     */
    List<NotificationLog> findByEventType(String eventType);
}
