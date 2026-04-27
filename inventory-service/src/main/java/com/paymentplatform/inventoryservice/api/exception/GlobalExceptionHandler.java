package com.paymentplatform.inventoryservice.api.exception;

import com.paymentplatform.commonlib.dto.ErrorResponse;
import com.paymentplatform.commonlib.exception.InventoryReservationException;
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
 * Centralised exception handler for inventory-service REST controllers.
 *
 * <p>Translates inventory-domain and validation exceptions into structured {@link ErrorResponse}
 * JSON bodies with consistent HTTP status codes.</p>
 *
 * <p><strong>Handled exceptions and their HTTP codes:</strong></p>
 * <ul>
 *   <li>{@link ResourceNotFoundException} → 404 Not Found (product/inventory row not found)</li>
 *   <li>{@link InventoryReservationException} → 409 Conflict (insufficient stock or reservation
 *       rule violated — raised during the two-pass reservation check)</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request (JSR-303 constraint)</li>
 *   <li>{@link Exception} (catch-all) → 500 Internal Server Error</li>
 * </ul>
 *
 * <p>Note: Reservations are ordinarily driven by Kafka events (not HTTP calls), so
 * {@link InventoryReservationException} is primarily surfaced from any direct REST paths
 * that trigger reservation logic.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles {@link ResourceNotFoundException} when a product or inventory record is not found.
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
     * Handles {@link InventoryReservationException} thrown when stock cannot be reserved —
     * for example when requested quantity exceeds available stock.
     * Returns 409 Conflict because the resource exists but the state prevents the operation.
     *
     * @param ex      exception describing the reservation failure
     * @param request current HTTP request
     * @return 409 Conflict response body
     */
    @ExceptionHandler(InventoryReservationException.class)
    public ResponseEntity<ErrorResponse> handleReservation(InventoryReservationException ex, HttpServletRequest request) {
        log.warn("Inventory reservation error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .status(409)
                        .error("Inventory Reservation Failed")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build());
    }

    /**
     * Handles JSR-303 bean validation failures, collecting all field constraint violations
     * into a single response so the caller can correct all errors at once.
     *
     * @param ex      validation exception with field error details
     * @param request current HTTP request
     * @return 400 Bad Request response body with {@code validationErrors} field map
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

        log.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(400)
                        .error("Validation Failed")
                        .message("Request validation failed")
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .validationErrors(fieldErrors)
                        .build());
    }

    /**
     * Catch-all handler for any unexpected {@link Exception} not covered by specific handlers.
     * Logs at ERROR level with full stack trace while returning a generic 500 to avoid leaking
     * internal details to the caller.
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
