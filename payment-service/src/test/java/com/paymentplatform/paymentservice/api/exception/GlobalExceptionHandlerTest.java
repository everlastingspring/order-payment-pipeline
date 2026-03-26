package com.paymentplatform.paymentservice.api.exception;

import com.paymentplatform.commonlib.dto.ErrorResponse;
import com.paymentplatform.commonlib.enums.PaymentStatus;
import com.paymentplatform.commonlib.exception.DuplicateRequestException;
import com.paymentplatform.commonlib.exception.PaymentFailedException;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Payment GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("not found returns 404")
    void handleNotFound_returns404() {
        MockHttpServletRequest request = request("/api/payments/123");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new ResourceNotFoundException("Payment", "123"), request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Not Found");
        assertThat(response.getBody().getPath()).isEqualTo("/api/payments/123");
    }

    @Test
    @DisplayName("duplicate request returns 409")
    void handleDuplicate_returns409() {
        MockHttpServletRequest request = request("/api/payments");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicate(
                new DuplicateRequestException("idem-1"), request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Conflict");
        assertThat(response.getBody().getMessage()).contains("idem-1");
    }

    @Test
    @DisplayName("payment failed returns 422")
    void handlePaymentFailed_returns422() {
        MockHttpServletRequest request = request("/api/payments");

        ResponseEntity<ErrorResponse> response = handler.handlePaymentFailed(
                new PaymentFailedException("payment-1", "declined", PaymentStatus.FAILED), request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Payment Failed");
        assertThat(response.getBody().getMessage()).contains("declined");
    }

    @Test
    @DisplayName("validation error returns field map")
    void handleValidation_returns400WithFieldErrors() throws Exception {
        MockHttpServletRequest request = request("/api/payments");
        MethodArgumentNotValidException exception = validationException("paymentMethod", "Payment method is required");

        ResponseEntity<ErrorResponse> response = handler.handleValidation(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getValidationErrors()).containsEntry("paymentMethod", "Payment method is required");
    }

    @Test
    @DisplayName("generic exception returns 500")
    void handleGeneric_returns500() {
        MockHttpServletRequest request = request("/api/payments");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(new RuntimeException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    private MethodArgumentNotValidException validationException(String field, String message) throws Exception {
        MethodParameter parameter = new MethodParameter(
                ValidationProbe.class.getDeclaredMethod("handle", Object.class), 0
        );
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "request");
        result.addError(new FieldError("request", field, message));
        return new MethodArgumentNotValidException(parameter, result);
    }

    private static final class ValidationProbe {
        @SuppressWarnings("unused")
        void handle(Object payload) {
        }
    }
}
