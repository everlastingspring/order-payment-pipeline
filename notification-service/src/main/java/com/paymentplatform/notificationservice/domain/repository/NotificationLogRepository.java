package com.paymentplatform.notificationservice.domain.repository;

import com.paymentplatform.notificationservice.domain.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    List<NotificationLog> findByNotificationIdOrderByCreatedAtDesc(UUID notificationId);

    List<NotificationLog> findByEventType(String eventType);
}
