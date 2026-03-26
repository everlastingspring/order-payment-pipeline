package com.paymentplatform.notificationservice.api.exception;

import com.paymentplatform.commonlib.dto.ErrorResponse;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Notification GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("ResourceNotFoundException Handler")
    class NotFoundExceptionHandler {
        @Test
        @DisplayName("should return 404 with correct error response")
        void handleNotFound_returns404() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/notifications/invalid-id");
            ResourceNotFoundException exception = new ResourceNotFoundException("Notification", "invalid-id");

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleNotFound(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody())
                    .isNotNull()
                    .satisfies(body -> {
                        assertThat(body.getStatus()).isEqualTo(404);
                        assertThat(body.getError()).isEqualTo("Not Found");
                        assertThat(body.getMessage()).isNotNull();
                        assertThat(body.getPath()).isEqualTo("/api/notifications/invalid-id");
                        assertThat(body.getTimestamp()).isNotNull();
                    });
        }
    }

    @Nested
    @DisplayName("Generic Exception Handler")
    class GenericExceptionHandler {
        @Test
        @DisplayName("should return 500 for unhandled exception")
        void handleGeneric_returns500() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/notifications");
            Exception exception = new RuntimeException("Database connection failed");

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleGeneric(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody())
                    .isNotNull()
                    .satisfies(body -> {
                        assertThat(body.getStatus()).isEqualTo(500);
                        assertThat(body.getError()).isEqualTo("Internal Server Error");
                        assertThat(body.getMessage()).isEqualTo("An unexpected error occurred");
                        assertThat(body.getPath()).isEqualTo("/api/notifications");
                        assertThat(body.getTimestamp()).isNotNull();
                    });
        }

        @Test
        @DisplayName("should handle NullPointerException gracefully")
        void handleGeneric_nullPointerException() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/notifications/123");
            Exception exception = new NullPointerException("Null value encountered");

            ResponseEntity<ErrorResponse> response = exceptionHandler.handleGeneric(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(500);
        }
    }
}
