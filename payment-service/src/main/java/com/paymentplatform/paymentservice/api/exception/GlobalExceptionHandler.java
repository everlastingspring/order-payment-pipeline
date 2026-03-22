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

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

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
