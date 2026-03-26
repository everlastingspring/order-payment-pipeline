package com.paymentplatform.walletservice.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.dto.*;
import com.paymentplatform.commonlib.enums.WalletTransactionType;
import com.paymentplatform.commonlib.exception.InsufficientFundsException;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.walletservice.application.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WalletController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("WalletController")
class WalletControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private WalletService walletService;

    private WalletDto stubWallet(UUID walletId) {
        return WalletDto.builder()
                .walletId(walletId.toString())
                .customerId("customer-001")
                .balance(MoneyDto.builder().amount(new BigDecimal("5000.00")).currency("INR").build())
                .build();
    }

    @Nested
    @DisplayName("GET /api/wallets/{walletId}")
    class GetWallet {

        @Test
        @DisplayName("returns 200 with wallet DTO")
        void returns200() throws Exception {
            UUID id = UUID.randomUUID();
            when(walletService.getWallet(id)).thenReturn(stubWallet(id));

            mockMvc.perform(get("/api/wallets/{walletId}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.walletId").value(id.toString()));
        }

        @Test
        @DisplayName("returns 404 when wallet not found")
        void returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(walletService.getWallet(id)).thenThrow(new ResourceNotFoundException("Wallet", id));

            mockMvc.perform(get("/api/wallets/{walletId}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("GET /api/wallets/customer/{customerId}")
    class GetWalletByCustomer {

        @Test
        @DisplayName("returns 200 with wallet DTO")
        void returns200() throws Exception {
            UUID id = UUID.randomUUID();
            when(walletService.getWalletByCustomer("customer-001")).thenReturn(stubWallet(id));

            mockMvc.perform(get("/api/wallets/customer/customer-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.customerId").value("customer-001"));
        }

        @Test
        @DisplayName("returns 404 when customer has no wallet")
        void returns404() throws Exception {
            when(walletService.getWalletByCustomer("unknown"))
                    .thenThrow(new ResourceNotFoundException("Wallet", "unknown"));

            mockMvc.perform(get("/api/wallets/customer/unknown"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("GET /api/wallets/customer/{customerId}/balance")
    class GetBalance {

        @Test
        @DisplayName("returns 200 with balance")
        void returns200() throws Exception {
            MoneyDto balance = MoneyDto.builder()
                    .amount(new BigDecimal("5000.00")).currency("INR").build();
            when(walletService.getBalance("customer-001")).thenReturn(balance);

            mockMvc.perform(get("/api/wallets/customer/customer-001/balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.amount").value(5000.00))
                    .andExpect(jsonPath("$.data.currency").value("INR"));
        }
    }

    @Nested
    @DisplayName("GET /api/wallets/{walletId}/ledger")
    class GetLedger {

        @Test
        @DisplayName("returns 200 with paged ledger entries")
        void returns200() throws Exception {
            UUID walletId = UUID.randomUUID();
            LedgerEntryDto entry = LedgerEntryDto.builder()
                    .entryId(UUID.randomUUID().toString())
                    .transactionType(WalletTransactionType.DEBIT)
                    .amount(MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build())
                    .referenceId("order:abc")
                    .build();
            Page<LedgerEntryDto> page = new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1);
            when(walletService.getLedgerEntries(eq(walletId), any())).thenReturn(page);

            mockMvc.perform(get("/api/wallets/{walletId}/ledger", walletId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("returns 404 when wallet not found")
        void returns404() throws Exception {
            UUID walletId = UUID.randomUUID();
            when(walletService.getLedgerEntries(eq(walletId), any()))
                    .thenThrow(new ResourceNotFoundException("Wallet", walletId));

            mockMvc.perform(get("/api/wallets/{walletId}/ledger", walletId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("POST /api/wallets/customer/{customerId}/topup")
    class TopUp {

        @Test
        @DisplayName("returns 200 on successful top-up")
        void returns200() throws Exception {
            UUID walletId = UUID.randomUUID();
            when(walletService.topUp(eq("customer-001"), any())).thenReturn(stubWallet(walletId));

            WalletTopUpRequest request = WalletTopUpRequest.builder()
                    .amount(new BigDecimal("5000.00")).currency("INR").build();

            mockMvc.perform(post("/api/wallets/customer/customer-001/topup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Wallet topped up successfully"));
        }

        @Test
        @DisplayName("returns 400 when amount is missing")
        void returns400MissingAmount() throws Exception {
            mockMvc.perform(post("/api/wallets/customer/customer-001/topup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currency\": \"INR\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/wallets/debit")
    class Debit {

        @Test
        @DisplayName("returns 200 on successful debit")
        void returns200() throws Exception {
            UUID walletId = UUID.randomUUID();
            when(walletService.debitForPayment(any())).thenReturn(stubWallet(walletId));

            WalletDebitRequest request = WalletDebitRequest.builder()
                    .customerId("customer-001")
                    .amount(new BigDecimal("4097.00"))
                    .currency("INR")
                    .orderId("order-123")
                    .build();

            mockMvc.perform(post("/api/wallets/debit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Wallet debited successfully"));
        }

        @Test
        @DisplayName("returns 422 when insufficient funds")
        void returns422InsufficientFunds() throws Exception {
            when(walletService.debitForPayment(any()))
                    .thenThrow(new InsufficientFundsException("wallet-1",
                            new BigDecimal("4097.00"), new BigDecimal("100.00")));

            WalletDebitRequest request = WalletDebitRequest.builder()
                    .customerId("customer-001")
                    .amount(new BigDecimal("4097.00"))
                    .currency("INR")
                    .orderId("order-123")
                    .build();

            mockMvc.perform(post("/api/wallets/debit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422));
        }
    }

    @Nested
    @DisplayName("POST /api/wallets/{walletId}/rebuild")
    class RebuildBalance {

        @Test
        @DisplayName("returns 200 on successful rebuild")
        void returns200() throws Exception {
            UUID walletId = UUID.randomUUID();
            when(walletService.rebuildBalance(walletId)).thenReturn(stubWallet(walletId));

            mockMvc.perform(post("/api/wallets/{walletId}/rebuild", walletId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Balance rebuilt from ledger"));
        }

        @Test
        @DisplayName("returns 404 when wallet not found")
        void returns404() throws Exception {
            UUID walletId = UUID.randomUUID();
            when(walletService.rebuildBalance(walletId))
                    .thenThrow(new ResourceNotFoundException("Wallet", walletId));

            mockMvc.perform(post("/api/wallets/{walletId}/rebuild", walletId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }
}
