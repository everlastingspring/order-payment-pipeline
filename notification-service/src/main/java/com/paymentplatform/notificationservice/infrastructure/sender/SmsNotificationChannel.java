package com.paymentplatform.notificationservice.infrastructure.sender;

import com.paymentplatform.commonlib.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Delivers high-priority notifications via SMS (Twilio / AWS SNS in production).
 *
 * Only critical payment alerts are sent over SMS — order lifecycle events
 * are email-only to avoid excessive messaging.
 *
 * Simulation: always succeeds. In production, replace the log call with
 * the Twilio SDK or AWS SNS publish.
 */
@Component
@Slf4j
public class SmsNotificationChannel implements NotificationChannel {

    private static final Set<NotificationType> SUPPORTED = Set.of(
            NotificationType.PAYMENT_SUCCESS,
            NotificationType.PAYMENT_FAILURE
    );

    @Override
    public boolean supports(NotificationType type) {
        return SUPPORTED.contains(type);
    }

    @Override
    public boolean send(String recipient, String subject, String body) {
        log.info("SMS notification sent: to={}, subject={}", recipient, subject);
        return true;
    }
}
