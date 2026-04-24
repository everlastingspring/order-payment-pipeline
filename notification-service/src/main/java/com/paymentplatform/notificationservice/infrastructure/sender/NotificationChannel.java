package com.paymentplatform.notificationservice.infrastructure.sender;

import com.paymentplatform.commonlib.enums.NotificationType;

/**
 * Strategy interface for notification delivery channels.
 *
 * Each channel (Email, SMS, Push) declares which notification types it handles
 * via supports(), and implements the actual send logic.
 *
 * To add a new channel (e.g. Push):
 *   1. Create PushNotificationChannel implements NotificationChannel
 *   2. Annotate with @Component
 *   3. Return true from supports() for the relevant types
 *   No changes needed in NotificationService.
 */
public interface NotificationChannel {

    /**
     * Returns true if this channel should handle the given notification type.
     */
    boolean supports(NotificationType type);

    /**
     * Sends the notification via this channel.
     *
     * @param recipient the destination address (email, phone number, device token)
     * @param subject   short summary line
     * @param body      full notification content
     * @return true if the send succeeded
     */
    boolean send(String recipient, String subject, String body);
}
