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

@Configuration(proxyBeanMethods = false)
public class OpenTelemetryConfig {

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

    @Bean
    ApplicationListener<ApplicationStartedEvent> openTelemetryAppenderInstaller(OpenTelemetry openTelemetry) {
        return event -> OpenTelemetryAppender.install(openTelemetry);
    }
}
