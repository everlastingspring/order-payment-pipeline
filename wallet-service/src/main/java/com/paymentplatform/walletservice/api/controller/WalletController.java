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

/**
 * REST controller exposing wallet read and write endpoints.
 *
 * <p>Base path: {@code /api/wallets}</p>
 *
 * <p><strong>Public endpoints</strong> (via API gateway, requires JWT):</p>
 * <ul>
 *   <li>{@code GET /api/wallets/{walletId}} — full wallet with balance</li>
 *   <li>{@code GET /api/wallets/customer/{customerId}} — wallet by customer</li>
 *   <li>{@code GET /api/wallets/customer/{customerId}/balance} — lightweight balance check</li>
 *   <li>{@code GET /api/wallets/{walletId}/ledger} — paginated transaction history</li>
 *   <li>{@code POST /api/wallets/customer/{customerId}/topup} — add funds</li>
 * </ul>
 *
 * <p><strong>Internal endpoints</strong> (called by other services, not customers):</p>
 * <ul>
 *   <li>{@code POST /api/wallets/debit} — called by payment-service during saga execution</li>
 *   <li>{@code POST /api/wallets/{walletId}/rebuild} — admin: recompute balance from ledger</li>
 * </ul>
 *
 * <p>In production the {@code /debit} endpoint should be protected by network policy
 * (K8s ClusterIP, not publicly reachable through the API gateway).</p>
 */
@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    /** Application service handling all wallet business logic. */
    private final WalletService walletService;

    // ── Read ──

    /**
     * Retrieves a wallet by its UUID.
     *
     * @param walletId UUID of the wallet
     * @return 200 OK with wallet DTO including balance and currency, or 404 if not found
     */
    @GetMapping("/{walletId}")
    public ResponseEntity<ApiResponse<WalletDto>> getWallet(@PathVariable("walletId") UUID walletId) {
        WalletDto wallet = walletService.getWallet(walletId);
        return ResponseEntity.ok(ApiResponse.<WalletDto>builder()
                .success(true).message("Wallet retrieved").data(wallet).build());
    }

    /**
     * Retrieves a wallet by customer ID.
     *
     * @param customerId customer identifier
     * @return 200 OK with wallet DTO, or 404 if no wallet exists for this customer
     */
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

    /**
     * Returns paginated ledger entries for a wallet, sorted newest first.
     * Used by the Postman smoke test to verify the debit entry after saga completion.
     *
     * @param walletId UUID of the wallet
     * @param page     zero-based page number (default 0)
     * @param size     page size (default 20)
     * @return 200 OK with paged ledger entry DTOs
     */
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
