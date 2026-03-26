package com.paymentplatform.paymentservice.application.processor;

import com.paymentplatform.commonlib.dto.WalletDto;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.exception.InsufficientFundsException;
import com.paymentplatform.paymentservice.infrastructure.client.WalletClient;
import com.paymentplatform.paymentservice.infrastructure.gateway.GatewayResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletPaymentProcessor")
class WalletPaymentProcessorTest {

    @Mock private WalletClient walletClient;

    @InjectMocks private WalletPaymentProcessor processor;

    private static final String ORDER_ID    = "order-abc-123";
    private static final String CUSTOMER_ID = "customer-001";
    private static final BigDecimal AMOUNT  = new BigDecimal("4097.00");
    private static final String CURRENCY    = "INR";

    // ── supports ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("supports")
    class Supports {

        @Test
        @DisplayName("returns true for WALLET")
        void walletMethod_returnsTrue() {
            assertThat(processor.supports(PaymentMethod.WALLET)).isTrue();
        }

        @ParameterizedTest(name = "returns false for {0}")
        @EnumSource(value = PaymentMethod.class, names = {"UPI", "CARD", "NET_BANKING", "EMI"})
        @DisplayName("returns false for all non-WALLET methods")
        void nonWalletMethods_returnFalse(PaymentMethod method) {
            assertThat(processor.supports(method)).isFalse();
        }
    }

    // ── charge ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("charge")
    class Charge {

        @Test
        @DisplayName("successful debit returns success GatewayResponse with WALLET- txn reference")
        void successful_returnsSuccessWithWalletTxnRef() {
            // debit() returns WalletDto — NOT void
            when(walletClient.debit(anyString(), any(), anyString(), anyString()))
                    .thenReturn(mock(WalletDto.class));

            GatewayResponse response = processor.charge(ORDER_ID, CUSTOMER_ID, AMOUNT, CURRENCY);

            assertThat(response.successful()).isTrue();
            assertThat(response.transactionReference()).startsWith("WALLET-");
            assertThat(response.failureCode()).isNull();
            assertThat(response.failureReason()).isNull();
        }

        @Test
        @DisplayName("debit is called with (customerId, amount, currency, orderId)")
        void debit_calledWithCorrectArguments() {
            when(walletClient.debit(anyString(), any(), anyString(), anyString()))
                    .thenReturn(mock(WalletDto.class));

            processor.charge(ORDER_ID, CUSTOMER_ID, AMOUNT, CURRENCY);

            verify(walletClient).debit(CUSTOMER_ID, AMOUNT, CURRENCY, ORDER_ID);
        }

        @Test
        @DisplayName("InsufficientFundsException returns failure GatewayResponse without rethrowing")
        void insufficientFunds_returnsFailureResponse_noException() {
            when(walletClient.debit(anyString(), any(), anyString(), anyString()))
                    .thenThrow(new InsufficientFundsException("wallet-1", AMOUNT, new BigDecimal("100.00")));

            GatewayResponse response = processor.charge(ORDER_ID, CUSTOMER_ID, AMOUNT, CURRENCY);

            assertThat(response.successful()).isFalse();
            assertThat(response.failureCode()).isEqualTo("INSUFFICIENT_WALLET_BALANCE");
            assertThat(response.failureReason()).contains("Wallet balance insufficient");
            assertThat(response.failureReason()).contains(AMOUNT.toPlainString());
        }

        @Test
        @DisplayName("any other exception from wallet-service re-throws as RuntimeException for circuit breaker")
        void walletServiceDown_throwsRuntimeException() {
            when(walletClient.debit(anyString(), any(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> processor.charge(ORDER_ID, CUSTOMER_ID, AMOUNT, CURRENCY))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(ORDER_ID);
        }

        @Test
        @DisplayName("txn reference is different on each successful charge (UUID-based)")
        void txnReference_isUniquePerCharge() {
            when(walletClient.debit(anyString(), any(), anyString(), anyString()))
                    .thenReturn(mock(WalletDto.class));

            GatewayResponse r1 = processor.charge(ORDER_ID, CUSTOMER_ID, AMOUNT, CURRENCY);
            GatewayResponse r2 = processor.charge(ORDER_ID, CUSTOMER_ID, AMOUNT, CURRENCY);

            assertThat(r1.transactionReference()).isNotEqualTo(r2.transactionReference());
        }
    }
}
