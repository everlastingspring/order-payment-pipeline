package com.paymentplatform.notificationservice.infrastructure.sender;

import com.paymentplatform.commonlib.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Delivers notifications via email (AWS SES / SendGrid in production).
 *
 * Delegates the actual send + Resilience4j circuit-breaker/retry behaviour
 * to NotificationSender, keeping those concerns in one place.
 *
 * Supports all notification types — email is the universal fallback channel.
 */
@Component
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private final NotificationSender sender;

    @Override
    public boolean supports(NotificationType type) {
        return true; // email handles every notification type
    }

    @Override
    public boolean send(String recipient, String subject, String body) {
        return sender.send(recipient, subject, body);
    }
}
