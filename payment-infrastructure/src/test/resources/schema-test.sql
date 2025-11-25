DROP TABLE IF EXISTS outbox_event;

CREATE TABLE outbox_event (
  oeid        BIGINT       NOT NULL,
  event_type  VARCHAR(255) NOT NULL,
  aggregate_id VARCHAR(255) NOT NULL,
  payload     TEXT         NOT NULL,
  status      VARCHAR(50)  NOT NULL,
  created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
  updated_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
  claimed_at  TIMESTAMP WITHOUT TIME ZONE NULL,
  claimed_by  VARCHAR(128) NULL,
  PRIMARY KEY (oeid, created_at)
);

-- indexes used by your queries
CREATE INDEX IF NOT EXISTS idx_outbox_event_createdat ON outbox_event (created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_event_status    ON outbox_event (status);
CREATE INDEX IF NOT EXISTS idx_outbox_status_claimed_at ON outbox_event (status, claimed_at);


-- payments + payment_orders tables used across mapper integration tests

DROP TABLE IF EXISTS payment_orders;
DROP TABLE IF EXISTS payments;

CREATE TABLE payments (
    payment_id BIGINT PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    buyer_id VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    total_amount_value BIGINT NOT NULL,
    captured_amount_value BIGINT NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    CONSTRAINT uq_payment_idempotency_key UNIQUE(idempotency_key)
);

CREATE TABLE payment_orders (
    payment_order_id BIGINT PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(payment_id) ON DELETE CASCADE,
    seller_id VARCHAR(255) NOT NULL,
    amount_value BIGINT NOT NULL,
    amount_currency CHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    retry_count INTEGER NOT NULL DEFAULT 0
);

-- Constraints matching changelog
ALTER TABLE payment_orders
    ADD CONSTRAINT chk_payment_orders_status_valid
    CHECK (status IN (
        'INITIATED_PENDING',
        'CAPTURE_REQUESTED',
        'CAPTURE_FAILED',
        'CAPTURED',
        'REFUND_REQUESTED',
        'REFUND_FAILED',
        'REFUNDED',
        'PENDING_CAPTURE',
        'TIMEOUT_EXCEEDED_1S_TRANSIENT',
        'PSP_UNAVAILABLE_TRANSIENT'
    ));

ALTER TABLE payment_orders
    ADD CONSTRAINT chk_payment_orders_currency_3
    CHECK (amount_currency ~ '^[A-Z]{3}$');

ALTER TABLE payment_orders
    ADD CONSTRAINT chk_payment_orders_amount_positive
    CHECK (amount_value > 0);

-- Indexes matching changelog
CREATE INDEX IF NOT EXISTS idx_payment_orders_payment_id ON payment_orders (payment_id);
CREATE INDEX IF NOT EXISTS idx_payment_orders_seller_id ON payment_orders (seller_id);

-- ========== JOURNAL ENTRIES TABLE ==========
DROP TABLE IF EXISTS postings;
DROP TABLE IF EXISTS ledger_entries;
DROP TABLE IF EXISTS journal_entries;

CREATE TABLE journal_entries (
	id VARCHAR(128) PRIMARY KEY,
	tx_type VARCHAR(32) NOT NULL,
	name VARCHAR(128),
	reference_type VARCHAR(64),
	reference_id VARCHAR(64),
	created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

-- ========== LEDGER ENTRIES TABLE ==========
CREATE TABLE ledger_entries (
	id BIGSERIAL PRIMARY KEY,
	journal_id VARCHAR(128) NOT NULL,
	created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
	CONSTRAINT fk_ledger_entries_journal FOREIGN KEY (journal_id) REFERENCES journal_entries(id) ON DELETE CASCADE
);

-- ========== POSTINGS TABLE ==========
CREATE TABLE postings (
	id BIGSERIAL PRIMARY KEY,
	journal_id VARCHAR(128) NOT NULL,
	account_code VARCHAR(128) NOT NULL,
	account_type VARCHAR(64) NOT NULL,
	amount BIGINT NOT NULL,
	direction VARCHAR(8) NOT NULL,
	currency VARCHAR(3) NOT NULL,
	created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
	CONSTRAINT fk_postings_journal FOREIGN KEY (journal_id) REFERENCES journal_entries(id) ON DELETE CASCADE,
	CONSTRAINT uq_postings_journal_account UNIQUE (journal_id, account_code)
);

-- Indexes for ledger tables
CREATE INDEX IF NOT EXISTS idx_postings_journal_id ON postings (journal_id);
CREATE INDEX IF NOT EXISTS idx_postings_account_code ON postings (account_code);

-- ========== ACCOUNT BALANCES TABLE ==========
DROP TABLE IF EXISTS account_balances;

CREATE TABLE account_balances (
    account_code VARCHAR(128) PRIMARY KEY,
    balance BIGINT NOT NULL,
    last_applied_entry_id BIGINT NOT NULL DEFAULT 0,
    last_snapshot_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

-- ========== ACCOUNT DIRECTORY TABLE ==========
DROP TABLE IF EXISTS account_directory;

CREATE TABLE account_directory (
    account_code VARCHAR(128) PRIMARY KEY,
    account_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    currency CHAR(3) NOT NULL,
    category VARCHAR(32),
    country VARCHAR(2),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

CREATE INDEX IF NOT EXISTS idx_account_directory_entity ON account_directory (entity_id);