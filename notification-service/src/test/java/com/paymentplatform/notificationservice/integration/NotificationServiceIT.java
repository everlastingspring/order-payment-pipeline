package com.paymentplatform.notificationservice.integration;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.enums.NotificationStatus;
import com.paymentplatform.commonlib.enums.NotificationType;
import com.paymentplatform.notificationservice.application.service.NotificationService;
import com.paymentplatform.notificationservice.domain.entity.Notification;
import com.paymentplatform.notificationservice.domain.entity.NotificationLog;
import com.paymentplatform.notificationservice.domain.repository.NotificationLogRepository;
import com.paymentplatform.notificationservice.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.fail-fast=false",
                "management.tracing.enabled=false",
                "management.metrics.export.prometheus.enabled=false"
        }
)
@Testcontainers(disabledWithoutDocker = false)
class NotificationServiceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("notification_db")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.4"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private static final String CUSTOMER_ID = "customer-it-001";
    private static final String CUSTOMER_EMAIL = "customer-it@example.com";
    private static final String REFERENCE_ID = KafkaTopics.ORDER_CREATED + ":order-it-001";

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @BeforeEach
    void setUp() {
        notificationLogRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    void sendNotification_persistsSentNotificationAndLog() {
        notificationService.sendNotification(
                CUSTOMER_ID,
                CUSTOMER_EMAIL,
                NotificationType.ORDER_CONFIRMATION,
                "Order Confirmed",
                "Your order has been confirmed",
                REFERENCE_ID,
                KafkaTopics.ORDER_CREATED,
                "evt-it-001"
        );

        List<Notification> notifications = notificationRepository.findByReferenceId(REFERENCE_ID);
        assertThat(notifications).singleElement().satisfies(notification -> {
            assertThat(notification.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(notification.getRecipientEmail()).isEqualTo(CUSTOMER_EMAIL);
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getAttemptCount()).isEqualTo(1);
        });

        Notification saved = notifications.get(0);
        List<NotificationLog> logs = notificationLogRepository.findByNotificationIdOrderByCreatedAtDesc(saved.getId());
        assertThat(logs).singleElement().satisfies(log -> {
            assertThat(log.getTopic()).isEqualTo(KafkaTopics.ORDER_CREATED);
            assertThat(log.getEventId()).isEqualTo("evt-it-001");
            assertThat(log.getPayload()).contains("confirmed");
        });
    }

    @Test
    void sendNotification_duplicateReferenceAndType_isIgnored() {
        notificationService.sendNotification(
                CUSTOMER_ID,
                CUSTOMER_EMAIL,
                NotificationType.ORDER_CONFIRMATION,
                "Order Received",
                "Your order is received",
                REFERENCE_ID,
                KafkaTopics.ORDER_CREATED,
                "evt-it-001"
        );

        notificationService.sendNotification(
                CUSTOMER_ID,
                CUSTOMER_EMAIL,
                NotificationType.ORDER_CONFIRMATION,
                "Order Received Again",
                "This should be deduplicated",
                REFERENCE_ID,
                KafkaTopics.ORDER_CREATED,
                "evt-it-002"
        );

        assertThat(notificationRepository.findByReferenceId(REFERENCE_ID)).hasSize(1);
        assertThat(notificationLogRepository.findAll()).hasSize(1);
    }
}
