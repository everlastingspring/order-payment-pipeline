package com.paymentplatform.paymentservice.infrastructure.client;

import com.paymentplatform.commonlib.dto.ApiResponse;
import com.paymentplatform.commonlib.dto.WalletDebitRequest;
import com.paymentplatform.commonlib.dto.WalletDto;
import com.paymentplatform.commonlib.exception.InsufficientFundsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * HTTP client for wallet-service.
 *
 * payment-service calls wallet-service synchronously during payment processing
 * to debit the customer's wallet balance.
 *
 * In local dev, wallet-service runs on port 8085.
 * In production (EKS), this will be the ClusterIP service DNS name.
 */
@Component
@Slf4j
public class WalletClient {

    private final RestClient restClient;

    public WalletClient(@Value("${services.wallet.base-url:http://localhost:8085}") String walletBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(walletBaseUrl)
                .build();
    }

    /**
     * Debits the customer's wallet for an order payment.
     * Returns the updated WalletDto on success.
     * Throws InsufficientFundsException if balance is too low.
     * Throws RuntimeException for any other failure.
     */
    public WalletDto debit(String customerId, BigDecimal amount, String currency, String orderId) {
        log.info("Requesting wallet debit: customerId={}, amount={} {}, orderId={}",
                customerId, amount, currency, orderId);

        WalletDebitRequest request = WalletDebitRequest.builder()
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .orderId(orderId)
                .build();

        return restClient.post()
                .uri("/api/wallets/debit")
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    // 400 = insufficient funds or bad request from wallet-service
                    log.warn("Wallet debit rejected: customerId={}, orderId={}, status={}",
                            customerId, orderId, res.getStatusCode());
                    throw new InsufficientFundsException("customer:" + customerId, amount, BigDecimal.ZERO);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("Wallet service error: customerId={}, orderId={}, status={}",
                            customerId, orderId, res.getStatusCode());
                    throw new RuntimeException("Wallet service unavailable — orderId=" + orderId);
                })
                .body(new ParameterizedTypeReference<ApiResponse<WalletDto>>() {})
                .getData();
    }
}
