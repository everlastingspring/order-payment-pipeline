package com.paymentplatform.inventoryservice.api.exception;

import com.paymentplatform.commonlib.dto.ErrorResponse;
import com.paymentplatform.commonlib.exception.InventoryReservationException;
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

@DisplayName("Inventory GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("not found returns 404")
    void handleNotFound_returns404() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new ResourceNotFoundException("Inventory", "inv-1"),
                request("/api/inventory/inv-1")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Not Found");
    }

    @Test
    @DisplayName("reservation failure returns 409")
    void handleReservation_returns409() {
        ResponseEntity<ErrorResponse> response = handler.handleReservation(
                new InventoryReservationException("sku-1", 5, 2),
                request("/api/inventory/reserve")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Inventory Reservation Failed");
    }

    @Test
    @DisplayName("validation returns field errors")
    void handleValidation_returnsFieldErrors() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleValidation(
                validationException("quantity", "Quantity is required"),
                request("/api/inventory/restock")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getValidationErrors()).containsEntry("quantity", "Quantity is required");
    }

    @Test
    @DisplayName("generic exception returns 500")
    void handleGeneric_returns500() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new RuntimeException("boom"),
                request("/api/inventory")
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
