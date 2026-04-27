package com.paymentplatform.paymentservice.api.exception;

import com.paymentplatform.commonlib.dto.ErrorResponse;
import com.paymentplatform.commonlib.exception.DuplicateRequestException;
import com.paymentplatform.commonlib.exception.PaymentFailedException;
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
 * Centralised exception handler for payment-service REST controllers.
 *
 * <p>Translates payment-domain and validation exceptions into structured {@link ErrorResponse} JSON
 * with consistent HTTP status codes. Notable additions versus a generic handler:</p>
 * <ul>
 *   <li>{@link DuplicateRequestException} → 409 Conflict — thrown when the idempotency key has
 *       already been consumed, protecting against double-charge on client retries.</li>
 *   <li>{@link PaymentFailedException} → 422 Unprocessable Entity — thrown by the payment
 *       processor when the wallet has insufficient funds or the gateway declines.</li>
 * </ul>
 *
 * <p><strong>Full exception → HTTP status mapping:</strong></p>
 * <ul>
 *   <li>{@link ResourceNotFoundException} → 404 Not Found</li>
 *   <li>{@link DuplicateRequestException} → 409 Conflict</li>
 *   <li>{@link PaymentFailedException} → 422 Unprocessable Entity</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request</li>
 *   <li>{@link Exception} (catch-all) → 500 Internal Server Error</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles {@link ResourceNotFoundException} when a payment or order cannot be found.
     *
     * @param ex      exception with the not-found detail message
     * @param request current HTTP request for path population
     * @return 404 response body
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
     * Handles {@link DuplicateRequestException} thrown by idempotency-key enforcement when
     * the same {@code Idempotency-Key} header is submitted more than once.
     * Returns 409 so the client knows the original request already succeeded and should not retry.
     *
     * @param ex      exception indicating idempotency-key collision
     * @param request current HTTP request
     * @return 409 Conflict response body
     */
    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateRequestException ex, HttpServletRequest request) {
        log.warn("Duplicate request: {}", ex.getMessage());
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
     * Handles {@link PaymentFailedException} thrown when the payment processor cannot complete
     * the charge — typically insufficient wallet balance or a simulated gateway decline.
     * Returns 422 (Unprocessable Entity) rather than 400 because the request itself was
     * well-formed; the business rule failed at execution time.
     *
     * @param ex      exception with the payment failure reason
     * @param request current HTTP request
     * @return 422 Unprocessable Entity response body
     */
    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ErrorResponse> handlePaymentFailed(PaymentFailedException ex, HttpServletRequest request) {
        log.warn("Payment failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.builder()
                        .status(422)
                        .error("Payment Failed")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build());
    }

    /**
     * Handles JSR-303 bean validation failures on {@code @Valid}-annotated request bodies.
     * Collects all field-level constraint violations into a map so the caller receives every
     * invalid field in a single response.
     *
     * @param ex      validation exception with field error details
     * @param request current HTTP request
     * @return 400 response body with {@code validationErrors} map of {@code field → message}
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
     * Logs at ERROR level with full stack trace for debugging while returning a generic 500
     * message to avoid leaking internal details to the caller.
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
