package com.paymentplatform.commonlib.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration that bridges the SLF4J/Logback logging stack with
 * OpenTelemetry log export so that structured log records are shipped to the OTLP collector
 * (and from there to Loki/Tempo).
 *
 * <p><strong>Architecture:</strong></p>
 * <ul>
 *   <li>Spring Boot's {@code spring.application.name} and trace ID are already injected into
 *       MDC by {@code spring-boot-starter-actuator} + {@code micrometer-tracing-bridge-otel}.</li>
 *   <li>This class adds the {@link OpenTelemetryAppender} (Logback appender) that forwards every
 *       log event to the {@link SdkLoggerProvider}, which batches them and ships via OTLP/HTTP.</li>
 *   <li>The appender is installed on {@link org.springframework.boot.context.event.ApplicationStartedEvent}
 *       so that all beans (including the OTel SDK) are fully initialised before attachment.</li>
 * </ul>
 *
 * <p>Endpoint defaults to {@code http://localhost:4318/v1/logs} for local dev.
 * In production the {@code management.otlp.logging.endpoint} property points to
 * {@code http://otel-collector:4318/v1/logs}.</p>
 */
@Configuration(proxyBeanMethods = false)
public class OpenTelemetryConfig {

    /**
     * Creates an {@link SdkLoggerProvider} that exports batched OTLP log records to the
     * configured endpoint. The resource (service name, instance ID) is injected from the
     * {@code Resource} bean created by Spring Boot's OTel auto-configuration.
     *
     * @param otelResource    Spring Boot's OTel resource descriptor (includes {@code service.name})
     * @param logsEndpoint    OTLP/HTTP logs endpoint from {@code management.otlp.logging.endpoint}
     * @return configured {@link SdkLoggerProvider}
     */
    @Bean
    SdkLoggerProvider sdkLoggerProvider(
            Resource otelResource,
            @Value("${management.otlp.logging.endpoint:http://localhost:4318/v1/logs}") String logsEndpoint) {
        return SdkLoggerProvider.builder()
                .setResource(otelResource)
                .addLogRecordProcessor(
                        BatchLogRecordProcessor.builder(
                                OtlpHttpLogRecordExporter.builder()
                                        .setEndpoint(logsEndpoint)
                                        .build()
                        ).build()
                )
                .build();
    }

    /**
     * Installs the {@link OpenTelemetryAppender} into the Logback context after the application
     * has fully started. This ensures the {@link OpenTelemetry} bean is available before the
     * appender starts forwarding log records.
     *
     * @param openTelemetry the fully configured OTel SDK instance
     * @return an event listener that performs the one-time appender installation
     */
    @Bean
    ApplicationListener<ApplicationStartedEvent> openTelemetryAppenderInstaller(OpenTelemetry openTelemetry) {
        return event -> OpenTelemetryAppender.install(openTelemetry);
    }
}
