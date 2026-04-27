package com.paymentplatform.walletservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for wallet-service.
 *
 * <p>Wallet-service manages customer wallet balances using an event-sourced ledger.
 * Balance is the cumulative aggregate of immutable {@code LedgerEntry} rows — the
 * {@code balance} column on {@code Wallet} is a materialized cache, recomputable
 * from the ledger at any time via {@code rebuildBalance()}.</p>
 *
 * <p>Wallet debits are performed synchronously via REST ({@code POST /api/wallets/debit})
 * called by payment-service during the saga. Refunds are triggered asynchronously by
 * consuming {@code order.cancelled} events from Kafka.</p>
 *
 * <p>No {@link EnableScheduling} — wallet-service has no scheduled background tasks.</p>
 *
 * <p>Runs on port {@code 8085} (local) or as configured in the deployment environment.</p>
 */
@SpringBootApplication
public class WalletServiceApplication {

    /**
     * Application entry point. Bootstraps the Spring context and starts all listeners
     * and embedded Tomcat.
     *
     * @param args command-line arguments passed through to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(WalletServiceApplication.class, args);
    }
}
