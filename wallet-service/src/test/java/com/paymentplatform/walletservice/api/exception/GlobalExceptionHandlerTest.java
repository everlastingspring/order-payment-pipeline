package com.paymentplatform.walletservice.api.exception;

import com.paymentplatform.commonlib.dto.ErrorResponse;
import com.paymentplatform.commonlib.exception.InsufficientFundsException;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Wallet GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("not found returns 404")
    void handleNotFound_returns404() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new ResourceNotFoundException("Wallet", "wallet-1"),
                request("/api/wallets/wallet-1")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Not Found");
    }

    @Test
    @DisplayName("insufficient funds returns 422")
    void handleInsufficientFunds_returns422() {
        ResponseEntity<ErrorResponse> response = handler.handleInsufficientFunds(
                new InsufficientFundsException("wallet-1", new BigDecimal("50.00"), new BigDecimal("10.00")),
                request("/api/wallets/debit")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Insufficient Funds");
        assertThat(response.getBody().getMessage()).contains("wallet-1");
    }

    @Test
    @DisplayName("validation error returns readable message")
    void handleValidation_returns400() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleValidationError(
                validationException("amount", "Amount is required"),
                request("/api/wallets/customer/customer-1/topup")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Validation Error");
        assertThat(response.getBody().getMessage()).contains("amount: Amount is required");
    }

    @Test
    @DisplayName("generic error returns 500")
    void handleGeneric_returns500() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new RuntimeException("boom"),
                request("/api/wallets")
        );

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
