-- EDGE DB SCHEMA

DROP TABLE IF EXISTS outbox_event CASCADE;
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
CREATE INDEX IF NOT EXISTS idx_outbox_event_createdat ON outbox_event (created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_event_status    ON outbox_event (status);
CREATE INDEX IF NOT EXISTS idx_outbox_status_claimed_at ON outbox_event (status, claimed_at);

DROP TABLE IF EXISTS payment_intents CASCADE;
CREATE TABLE payment_intents (
    payment_intent_id BIGINT PRIMARY KEY,
    buyer_id VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    merchant_account_id VARCHAR(255) NOT NULL,
    processing_model VARCHAR(255) NOT NULL,
    psp_reference VARCHAR(255),
    total_amount_value BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    splits_json JSONB,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    CONSTRAINT chk_payment_intent_status_valid CHECK (status IN ('CREATED_PENDING', 'CREATED', 'PENDING_AUTH', 'AUTHORIZED', 'DECLINED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_payment_intent_currency_3 CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_payment_intent_total_amount_value_le_total CHECK (total_amount_value >= 0 AND total_amount_value <= total_amount_value)
);
CREATE INDEX IF NOT EXISTS idx_payment_intents_psp_reference ON payment_intents (psp_reference);

DROP TABLE IF EXISTS idempotency_keys CASCADE;
CREATE TABLE idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    payment_intent_id BIGINT NULL,
    request_hash VARCHAR(128) NULL,
    response_payload JSONB NULL,
    status VARCHAR(20) NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    CONSTRAINT fk_idempotency_keys_payment_intent FOREIGN KEY (payment_intent_id) REFERENCES payment_intents(payment_intent_id),
    CONSTRAINT chk_idem_req_status CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_idempotency_request_hash ON idempotency_keys (request_hash);
CREATE INDEX IF NOT EXISTS idx_idempotency_payment_intent_id ON idempotency_keys (payment_intent_id);
