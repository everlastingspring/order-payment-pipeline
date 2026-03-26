package com.paymentplatform.walletservice.api.controller;

import com.paymentplatform.commonlib.dto.*;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.walletservice.application.service.WalletService;
import jakarta.validation.Valid;
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

    // ── Read ──

    @GetMapping("/{walletId}")
    public ResponseEntity<ApiResponse<WalletDto>> getWallet(@PathVariable("walletId") UUID walletId) {
        WalletDto wallet = walletService.getWallet(walletId);
        return ResponseEntity.ok(ApiResponse.<WalletDto>builder()
                .success(true).message("Wallet retrieved").data(wallet).build());
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<WalletDto>> getWalletByCustomer(
            @PathVariable("customerId") String customerId) {
        WalletDto wallet = walletService.getWalletByCustomer(customerId);
        return ResponseEntity.ok(ApiResponse.<WalletDto>builder()
                .success(true).message("Wallet retrieved").data(wallet).build());
    }

    /**
     * Lightweight balance check — just the current balance.
     * Use this for pre-order checks instead of loading the full wallet object.
     *
     * GET /api/wallets/customer/{customerId}/balance
     * Response: { "data": { "amount": 5903.00, "currency": "INR" } }
     */
    @GetMapping("/customer/{customerId}/balance")
    public ResponseEntity<ApiResponse<MoneyDto>> getBalance(
            @PathVariable("customerId") String customerId) {
        MoneyDto balance = walletService.getBalance(customerId);
        return ResponseEntity.ok(ApiResponse.<MoneyDto>builder()
                .success(true).message("Balance retrieved").data(balance).build());
    }

    @GetMapping("/{walletId}/ledger")
    public ResponseEntity<ApiResponse<PagedResponse<LedgerEntryDto>>> getLedger(
            @PathVariable("walletId") UUID walletId,
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
                .success(true).message("Ledger entries retrieved").data(pagedResponse).build());
    }

    // ── Write ──

    /**
     * Top-up: customer adds money to their wallet.
     *
     * POST /api/wallets/customer/{customerId}/topup
     * Body: { "amount": 5000, "currency": "INR", "description": "Monthly credit" }
     */
    @PostMapping("/customer/{customerId}/topup")
    public ResponseEntity<ApiResponse<WalletDto>> topUp(
            @PathVariable("customerId") String customerId,
            @Valid @RequestBody WalletTopUpRequest request) {

        WalletDto wallet = walletService.topUp(customerId, request);
        return ResponseEntity.ok(ApiResponse.<WalletDto>builder()
                .success(true)
                .message("Wallet topped up successfully")
                .data(wallet)
                .build());
    }

    /**
     * Internal debit endpoint — called by payment-service ONLY.
     * Not for direct customer use. In production, restrict via network policy.
     *
     * POST /api/wallets/debit
     * Body: { "customerId": "...", "amount": 4097, "currency": "INR", "orderId": "..." }
     */
    @PostMapping("/debit")
    public ResponseEntity<ApiResponse<WalletDto>> debit(
            @RequestBody WalletDebitRequest request) {

        WalletDto wallet = walletService.debitForPayment(request);
        return ResponseEntity.ok(ApiResponse.<WalletDto>builder()
                .success(true)
                .message("Wallet debited successfully")
                .data(wallet)
                .build());
    }

    /**
     * Admin: rebuild balance from ledger (event sourcing replay).
     */
    @PostMapping("/{walletId}/rebuild")
    public ResponseEntity<ApiResponse<WalletDto>> rebuildBalance(
            @PathVariable("walletId") UUID walletId) {
        WalletDto wallet = walletService.rebuildBalance(walletId);
        return ResponseEntity.ok(ApiResponse.<WalletDto>builder()
                .success(true).message("Balance rebuilt from ledger").data(wallet).build());
    }
}
