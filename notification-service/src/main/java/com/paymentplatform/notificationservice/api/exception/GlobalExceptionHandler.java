package com.paymentplatform.notificationservice.api.exception;

import com.paymentplatform.commonlib.dto.ErrorResponse;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Centralised exception handler for notification-service REST controllers.
 *
 * <p>Notification-service exposes a read-only HTTP API (GET endpoints only); most of its
 * processing happens asynchronously via Kafka consumers. As a result, the exception surface
 * is deliberately small — only {@link ResourceNotFoundException} needs a dedicated handler.</p>
 *
 * <p><strong>Handled exceptions:</strong></p>
 * <ul>
 *   <li>{@link ResourceNotFoundException} → 404 Not Found (notification record not found by ID)</li>
 *   <li>{@link Exception} (catch-all) → 500 Internal Server Error</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles {@link ResourceNotFoundException} when a notification cannot be found by the
     * requested identifier or customer ID.
     *
     * @param ex      exception with the not-found detail message
     * @param request current HTTP request for path population
     * @return 404 Not Found response body
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .status(404)
                        .error("Not Found")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build());
    }

    /**
     * Catch-all handler for any unexpected {@link Exception}.
     * Logs at ERROR level with full stack trace while returning a generic 500 to avoid
     * exposing internal details to the caller.
     *
     * @param ex      the unexpected exception
     * @param request current HTTP request
     * @return 500 Internal Server Error response body
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .status(500)
                        .error("Internal Server Error")
                        .message("An unexpected error occurred")
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build());
    }
}
