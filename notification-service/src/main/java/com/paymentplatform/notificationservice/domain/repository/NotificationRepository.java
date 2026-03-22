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

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    List<Notification> findByReferenceId(String referenceId);

    List<Notification> findByStatus(NotificationStatus status);

    boolean existsByReferenceIdAndType(String referenceId, NotificationType type);
}
