package com.paymentplatform.paymentservice.infrastructure.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentGatewaySimulator Tests")
class PaymentGatewaySimulatorTest {

    private final PaymentGatewaySimulator simulator = new PaymentGatewaySimulator();

    @Test
    @DisplayName("charge succeeds for non .99 amounts")
    void charge_succeedsForNormalAmount() {
        GatewayResponse response = simulator.charge("order-1", new BigDecimal("100.00"), "INR", null);

        assertThat(response.successful()).isTrue();
        assertThat(response.transactionReference()).startsWith("TXN-");
        assertThat(response.failureCode()).isNull();
    }

    @Test
    @DisplayName("charge fails for amounts ending in .99")
    void charge_failsForDot99Amount() {
        GatewayResponse response = simulator.charge("order-1", new BigDecimal("10.99"), "INR", null);

        assertThat(response.successful()).isFalse();
        assertThat(response.failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(response.failureReason()).contains("insufficient funds");
    }
}
