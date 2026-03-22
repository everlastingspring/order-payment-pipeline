-- =============================================
-- Wallet Service Schema (Event Sourced)
-- Tables: wallets, ledger_entries
--
-- The ledger_entries table is the source of truth.
-- wallets.balance is a cached projection for fast reads.
-- To rebuild: SELECT SUM(signed_amount) FROM ledger_entries WHERE wallet_id = ?
-- =============================================

CREATE TABLE wallets (
    id          UUID PRIMARY KEY,
    customer_id VARCHAR(255)   NOT NULL UNIQUE,
    balance     DECIMAL(19,2)  NOT NULL DEFAULT 0.00,
    currency    VARCHAR(3)     NOT NULL DEFAULT 'INR',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE ledger_entries (
    id                UUID PRIMARY KEY,
    wallet_id         UUID           NOT NULL REFERENCES wallets(id),
    transaction_type  VARCHAR(50)    NOT NULL,
    amount            DECIMAL(19,2)  NOT NULL,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'INR',
    balance_after     DECIMAL(19,2)  NOT NULL,
    reference_id      VARCHAR(255)   NOT NULL,
    description       TEXT,
    occurred_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_wallets_customer        ON wallets(customer_id);
CREATE INDEX idx_ledger_wallet_id        ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_reference        ON ledger_entries(reference_id);
CREATE INDEX idx_ledger_type             ON ledger_entries(transaction_type);
CREATE INDEX idx_ledger_occurred         ON ledger_entries(occurred_at);
CREATE UNIQUE INDEX idx_ledger_idempotent ON ledger_entries(wallet_id, reference_id, transaction_type);
