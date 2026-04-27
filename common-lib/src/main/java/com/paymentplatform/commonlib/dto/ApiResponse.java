package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Generic wrapper for all REST API responses in the platform.
 *
 * <p>Every controller returns this type so clients have a consistent envelope:</p>
 * <pre>
 * {
 *   "success": true,
 *   "message": "Order created successfully",
 *   "data": { ... },
 *   "timestamp": "2026-03-21T10:00:00Z"
 * }
 * </pre>
 *
 * <p>On error, {@code success=false}, {@code data=null}, {@code message} describes the problem.
 * Prefer {@link com.paymentplatform.commonlib.dto.ErrorResponse} for structured validation errors.</p>
 *
 * @param <T> the type of the response body payload
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** {@code true} for 2xx responses, {@code false} for all error responses. */
    private boolean success;

    /** Optional human-readable summary (e.g. "Order created successfully"). Omitted if null. */
    private String message;

    /** The actual response payload — null on errors (omitted from JSON via NON_NULL). */
    private T data;

    /** Server-side timestamp when the response was generated. */
    private Instant timestamp;

    /**
     * Creates a successful response wrapping the given data.
     *
     * @param data the payload to return
     * @param <T>  payload type
     * @return response with {@code success=true}, no message
     */
    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a successful response with both data and a descriptive message.
     *
     * @param data    the payload to return
     * @param message human-readable summary shown to the client
     * @param <T>     payload type
     * @return response with {@code success=true}
     */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates an error response with no data payload.
     *
     * @param message human-readable error description
     * @param <T>     type parameter (unused — data will be null)
     * @return response with {@code success=false}, {@code data=null}
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
