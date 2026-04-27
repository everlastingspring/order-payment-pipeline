package com.paymentplatform.orderservice.api.exception;

import com.paymentplatform.commonlib.dto.ErrorResponse;
import com.paymentplatform.commonlib.exception.InvalidOrderStateException;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception handler for order-service REST controllers.
 *
 * <p>Translates domain and validation exceptions into structured {@link ErrorResponse} JSON bodies
 * with consistent HTTP status codes. All error bodies share the same shape so API consumers
 * can parse them uniformly regardless of which handler was triggered.</p>
 *
 * <p><strong>Handled exceptions and their HTTP codes:</strong></p>
 * <ul>
 *   <li>{@link ResourceNotFoundException} → 404 Not Found (order/item does not exist)</li>
 *   <li>{@link InvalidOrderStateException} → 409 Conflict (e.g. cancelling a FAILED order)</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request (JSR-303 constraint violation)</li>
 *   <li>{@link Exception} (catch-all) → 500 Internal Server Error</li>
 * </ul>
 *
 * <p>Deliberately does NOT catch Kafka or outbox exceptions — those are handled internally
 * by {@code OutboxProcessor} with retry logic and are never surfaced to HTTP callers.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles {@link ResourceNotFoundException} thrown when an order or order item cannot be
     * found by the requested identifier.
     *
     * @param ex      the exception carrying the human-readable detail message
     * @param request the current HTTP request (used to populate {@code path} in the response)
     * @return 404 response body with the exception message
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
     * Handles {@link InvalidOrderStateException} thrown when a state-transition is attempted on
     * an order that is already in a terminal or incompatible status (e.g. cancelling FAILED).
     *
     * @param ex      the exception with the violated state transition detail
     * @param request the current HTTP request
     * @return 409 response body describing the state conflict
     */
    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidOrderStateException ex, HttpServletRequest request) {
        log.warn("Invalid order state: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .status(409)
                        .error("Conflict")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build());
    }

    /**
     * Handles JSR-303 bean validation failures on {@code @Valid}-annotated request bodies.
     * Collects all field-level constraint violations into a map so the caller can see every
     * invalid field in a single response rather than receiving errors one at a time.
     *
     * @param ex      the validation exception containing field error details
     * @param request the current HTTP request
     * @return 400 response body with a {@code validationErrors} map of {@code field → message}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(400)
                        .error("Bad Request")
                        .message("Validation failed")
                        .path(request.getRequestURI())
                        .validationErrors(errors)
                        .timestamp(Instant.now())
                        .build());
    }

    /**
     * Catch-all handler for any unhandled {@link Exception} that escapes a controller.
     * Logs the full stack trace at ERROR level for post-mortem debugging while returning a
     * generic 500 message to the client so internal details are not leaked.
     *
     * @param ex      the unexpected exception
     * @param request the current HTTP request
     * @return 500 response body with a generic "unexpected error" message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
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
