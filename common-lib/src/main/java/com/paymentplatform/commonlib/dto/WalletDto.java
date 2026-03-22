package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletDto {

    private String walletId;
    private String customerId;
    private MoneyDto balance;
    private List<LedgerEntryDto> recentTransactions;
    private Instant createdAt;
    private Instant updatedAt;
}
