package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Structured error body returned by all exception handlers and security entry points.
 *
 * <p>Returned for 4xx/5xx responses so clients receive a consistent JSON structure:</p>
 * <pre>
 * {
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Order not found with id: abc123",
 *   "path": "/api/orders/abc123",
 *   "timestamp": "2026-03-21T10:00:00Z"
 * }
 * </pre>
 *
 * <p>Validation errors include an additional {@link #validationErrors} map
 * keyed by field name:</p>
 * <pre>
 * { "validationErrors": { "customerId": "must not be blank", "items": "must not be empty" } }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /** HTTP status code (e.g. 400, 401, 403, 404, 500). */
    private int status;

    /** Short HTTP reason phrase (e.g. "Not Found", "Bad Request"). */
    private String error;

    /** Detailed error description — safe to show to the client. */
    private String message;

    /** Request URI that triggered the error. */
    private String path;

    /**
     * Per-field validation messages, populated only for 400 Bad Request responses
     * triggered by {@code @Valid} constraint violations.
     * Omitted from JSON when null.
     */
    private Map<String, String> validationErrors;

    /** Server-side timestamp when the error was generated. */
    private Instant timestamp;
}
