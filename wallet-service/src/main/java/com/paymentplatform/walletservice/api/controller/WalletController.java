package com.paymentplatform.walletservice.api.controller;

import com.paymentplatform.commonlib.dto.ApiResponse;
import com.paymentplatform.commonlib.dto.LedgerEntryDto;
import com.paymentplatform.commonlib.dto.PagedResponse;
import com.paymentplatform.commonlib.dto.WalletDto;
import com.paymentplatform.walletservice.application.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/{walletId}")
    public ResponseEntity<ApiResponse<WalletDto>> getWallet(@PathVariable UUID walletId) {
        WalletDto wallet = walletService.getWallet(walletId);
        return ResponseEntity.ok(ApiResponse.<WalletDto>builder()
                .success(true)
                .message("Wallet retrieved")
                .data(wallet)
                .build());
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<WalletDto>> getWalletByCustomer(@PathVariable String customerId) {
        WalletDto wallet = walletService.getWalletByCustomer(customerId);
        return ResponseEntity.ok(ApiResponse.<WalletDto>builder()
                .success(true)
                .message("Wallet retrieved")
                .data(wallet)
                .build());
    }

    @GetMapping("/{walletId}/ledger")
    public ResponseEntity<ApiResponse<PagedResponse<LedgerEntryDto>>> getLedger(
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<LedgerEntryDto> entries = walletService.getLedgerEntries(walletId, pageable);

        PagedResponse<LedgerEntryDto> pagedResponse = PagedResponse.<LedgerEntryDto>builder()
                .content(entries.getContent())
                .page(entries.getNumber())
                .size(entries.getSize())
                .totalElements(entries.getTotalElements())
                .totalPages(entries.getTotalPages())
                .last(entries.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.<PagedResponse<LedgerEntryDto>>builder()
                .success(true)
                .message("Ledger entries retrieved")
                .data(pagedResponse)
                .build());
    }

    @PostMapping("/{walletId}/rebuild")
    public ResponseEntity<ApiResponse<WalletDto>> rebuildBalance(@PathVariable UUID walletId) {
        WalletDto wallet = walletService.rebuildBalance(walletId);
        return ResponseEntity.ok(ApiResponse.<WalletDto>builder()
                .success(true)
                .message("Balance rebuilt from ledger")
                .data(wallet)
                .build());
    }
}
