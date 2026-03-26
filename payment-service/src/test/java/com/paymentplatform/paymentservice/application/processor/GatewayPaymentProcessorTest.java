package com.paymentplatform.paymentservice.application.processor;

import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.paymentservice.infrastructure.gateway.GatewayResponse;
import com.paymentplatform.paymentservice.infrastructure.gateway.PaymentGatewaySimulator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayPaymentProcessor Tests")
class GatewayPaymentProcessorTest {

    @Mock
    private PaymentGatewaySimulator simulator;

    @InjectMocks
    private GatewayPaymentProcessor processor;

    @Test
    @DisplayName("supports returns false for all methods until gateway is enabled")
    void supports_returnsFalseForAllMethods() {
        for (PaymentMethod method : PaymentMethod.values()) {
            assertThat(processor.supports(method)).isFalse();
        }
    }

    @Test
    @DisplayName("charge delegates to simulator with null payment method")
    void charge_delegatesToSimulator() {
        GatewayResponse expected = GatewayResponse.success("TXN-123");
        when(simulator.charge("order-1", new BigDecimal("100.00"), "INR", null)).thenReturn(expected);

        GatewayResponse response = processor.charge("order-1", "customer-1", new BigDecimal("100.00"), "INR");

        assertThat(response).isEqualTo(expected);
        verify(simulator).charge("order-1", new BigDecimal("100.00"), "INR", null);
    }
}
