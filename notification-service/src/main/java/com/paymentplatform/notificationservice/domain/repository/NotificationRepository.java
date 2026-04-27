package com.paymentplatform.notificationservice.domain.repository;

import com.paymentplatform.commonlib.enums.NotificationStatus;
import com.paymentplatform.commonlib.enums.NotificationType;
import com.paymentplatform.notificationservice.domain.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Notification} entities.
 *
 * <p>Notifications are created when Kafka events arrive at the consumer and are updated
 * as they progress through sending attempts. The combination of {@code referenceId}
 * (typically orderId or paymentId) and {@code type} forms the deduplication key —
 * see {@link #existsByReferenceIdAndType(String, NotificationType)}.</p>
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Returns all notifications for a given customer, newest first.
     * Used by the customer notification-history endpoint.
     *
     * @param customerId external customer identifier
     * @param pageable   pagination and sorting parameters
     * @return page of matching notifications ordered by {@code createdAt DESC}
     */
    Page<Notification> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    /**
     * Finds all notifications linked to a given reference (e.g. all notifications for an order).
     * Used when reprocessing or auditing events associated with a specific business object.
     *
     * @param referenceId the business object identifier (orderId, paymentId, etc.)
     * @return list of matching notifications
     */
    List<Notification> findByReferenceId(String referenceId);

    /**
     * Returns all notifications in a given status.
     * Useful for operational queries such as finding all {@code FAILED} notifications
     * pending manual investigation.
     *
     * @param status the notification status filter
     * @return list of matching notifications
     */
    List<Notification> findByStatus(NotificationStatus status);

    /**
     * Deduplication check — returns {@code true} if a notification of the given type has
     * already been recorded for the given reference. Called before creating a new notification
     * record to prevent duplicate sends when Kafka redelivers the same event.
     *
     * @param referenceId the business object identifier (orderId, paymentId, etc.)
     * @param type        the notification type (e.g. ORDER_CREATED, PAYMENT_SUCCESS)
     * @return {@code true} if a notification already exists for this reference + type pair
     */
    boolean existsByReferenceIdAndType(String referenceId, NotificationType type);
}
