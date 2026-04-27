package com.paymentplatform.walletservice.api.exception;

import com.paymentplatform.commonlib.dto.ErrorResponse;
import com.paymentplatform.commonlib.exception.InsufficientFundsException;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Centralised exception handler for wallet-service REST controllers.
 *
 * <p>Translates wallet-domain and validation exceptions into structured {@link ErrorResponse}
 * JSON bodies with consistent HTTP status codes.</p>
 *
 * <p><strong>Handled exceptions and their HTTP codes:</strong></p>
 * <ul>
 *   <li>{@link ResourceNotFoundException} → 404 Not Found (wallet not found)</li>
 *   <li>{@link InsufficientFundsException} → 422 Unprocessable Entity — raised by
 *       {@code Wallet.debit()} when the debit amount exceeds the current balance.
 *       422 is chosen over 400 because the request is structurally valid; the wallet's
 *       runtime state prevents execution.</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request (JSR-303 constraint)</li>
 *   <li>{@link Exception} (catch-all) → 500 Internal Server Error</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles {@link ResourceNotFoundException} when a wallet cannot be found by the
     * requested identifier.
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
     * Handles {@link InsufficientFundsException} thrown when a debit or top-up operation
     * cannot be completed because the wallet balance is too low.
     * Returns 422 Unprocessable Entity — the request was valid but the business rule failed.
     *
     * @param ex      exception describing the funds shortfall
     * @param request current HTTP request
     * @return 422 Unprocessable Entity response body
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex, HttpServletRequest request) {
        log.warn("Insufficient funds: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.builder()
                        .status(422)
                        .error("Insufficient Funds")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build());
    }

    /**
     * Handles JSR-303 bean validation failures on {@code @Valid}-annotated request bodies.
     * Concatenates all field constraint messages into a single comma-separated string.
     *
     * @param ex      validation exception with field error details
     * @param request current HTTP request
     * @return 400 Bad Request response body with combined field error message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation error: {}", ex.getMessage());
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(400)
                        .error("Validation Error")
                        .message(message)
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build());
    }

    /**
     * Catch-all handler for any unexpected {@link Exception} not covered by specific handlers.
     * Logs at ERROR level with full stack trace while returning a generic 500 to the caller.
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
