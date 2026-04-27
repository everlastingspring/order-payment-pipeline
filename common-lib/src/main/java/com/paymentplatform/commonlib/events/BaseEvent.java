package com.paymentplatform.commonlib.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Abstract base for all domain events published across Kafka topics.
 *
 * <p>Every event in the platform inherits these fields to support:
 * <ul>
 *   <li><b>Deduplication</b>: {@code eventId} uniquely identifies the event so
 *       consumers can detect and skip redeliveries.</li>
 *   <li><b>Routing &amp; logging</b>: {@code eventType} mirrors the Kafka topic name,
 *       making logs and outbox records self-describing.</li>
 *   <li><b>Tracing</b>: {@code correlationId} threads a single business request
 *       (e.g. one order flow) across all Kafka hops.</li>
 *   <li><b>Schema evolution</b>: {@code version} lets consumers handle older
 *       payloads gracefully when field shapes change.</li>
 * </ul>
 *
 * <p>Subclasses use Lombok's {@code @SuperBuilder} so the base fields are
 * included in the builder automatically.</p>
 */
@Getter
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseEvent {

    /** Globally unique identifier for this specific event occurrence. Used for deduplication. */
    private UUID eventId;

    /** Mirrors the Kafka topic name (e.g. "order.created"). Set by each subclass constructor. */
    private String eventType;

    /** Wall-clock time when the event was created. Set to {@code Instant.now()} at construction. */
    private Instant occurredAt;

    /** Schema version — increment when adding breaking changes to the payload. Starts at 1. */
    private int version;

    /**
     * Optional trace ID linking related events across the full saga.
     * Populated from OpenTelemetry baggage in production; null in unit tests.
     */
    private String correlationId;

    protected BaseEvent(String eventType) {
        this.eventId = UUID.randomUUID();
        this.eventType = eventType;
        this.occurredAt = Instant.now();
        this.version = 1;
    }
}
