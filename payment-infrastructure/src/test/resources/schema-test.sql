DROP TABLE IF EXISTS outbox_event;

CREATE TABLE outbox_event (
  oeid        BIGINT       NOT NULL,
  event_type  VARCHAR(255) NOT NULL,
  aggregate_id VARCHAR(255) NOT NULL,
  payload     TEXT         NOT NULL,
  status      VARCHAR(50)  NOT NULL,
  created_at  TIMESTAMP    NOT NULL,
  claimed_at  TIMESTAMP NULL,
  claimed_by  VARCHAR(128) NULL,
  PRIMARY KEY (oeid, created_at)
);

-- indexes used by your queries
CREATE INDEX IF NOT EXISTS idx_outbox_event_createdat ON outbox_event (created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_event_status    ON outbox_event (status);
CREATE INDEX IF NOT EXISTS idx_outbox_status_claimed_at ON outbox_event (status, claimed_at);


-- public.payment_orders definition

-- Drop table

DROP TABLE IF EXISTS payments;

CREATE TABLE payments (
	id BIGSERIAL PRIMARY KEY,
	payment_id BIGINT NOT NULL,
	public_payment_id VARCHAR(255) NOT NULL UNIQUE,
	buyer_id VARCHAR(255) NOT NULL,
	order_id VARCHAR(255) NOT NULL,
	amount_value BIGINT NOT NULL,
	amount_currency VARCHAR(3) NOT NULL,
	status VARCHAR(50) NOT NULL,
	created_at TIMESTAMP NOT NULL,
	updated_at TIMESTAMP NULL,
	retry_count INT NULL,
	retry_reason VARCHAR(255) NULL,
	last_error_message VARCHAR(255) NULL
);
DROP TABLE IF EXISTS  payment_orders;
CREATE TABLE payment_orders (
	payment_order_id int8 NOT NULL,
	public_payment_order_id varchar(255) NOT NULL,
	payment_id int8 NOT NULL,
	public_payment_id varchar(255) NOT NULL,
	seller_id varchar(255) NOT NULL,
	amount_value numeric(19, 2) NOT NULL,
	amount_currency varchar(10) NOT NULL,
	status varchar(50) NOT NULL,
	created_at timestamp NOT NULL,
	updated_at timestamp NULL,
	retry_count int4 NULL,
	retry_reason varchar(255) NULL,
	last_error_message varchar(255) NULL,
	CONSTRAINT payment_orders_pkey PRIMARY KEY (payment_order_id),
	CONSTRAINT payment_orders_public_payment_order_id_key UNIQUE (public_payment_order_id)
);

-- ========== JOURNAL ENTRIES TABLE ==========
DROP TABLE IF EXISTS postings;
DROP TABLE IF EXISTS journal_entries;

CREATE TABLE journal_entries (
	id VARCHAR(128) PRIMARY KEY,
	tx_type VARCHAR(32) NOT NULL,
	name VARCHAR(128),
	reference_type VARCHAR(64),
	reference_id VARCHAR(64),
	created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
	created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT fk_postings_journal FOREIGN KEY (journal_id) REFERENCES journal_entries(id) ON DELETE CASCADE,
	CONSTRAINT uq_postings_journal_account UNIQUE (journal_id, account_code)
);

-- Indexes for ledger tables
CREATE INDEX IF NOT EXISTS idx_postings_journal_id ON postings (journal_id);
CREATE INDEX IF NOT EXISTS idx_postings_account_code ON postings (account_code);