-- outbox_event
CREATE TABLE outbox_event (
    id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id, created_at)
);
CREATE INDEX idx_outbox_event_createdat ON outbox_event (created_at);
CREATE INDEX idx_outbox_event_status ON outbox_event (status);
CREATE INDEX idx_outbox_status_createdat ON outbox_event (status, created_at);

-- payments
CREATE TABLE payments (
    payment_id BIGINT PRIMARY KEY,
    public_payment_id VARCHAR(255) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    buyer_id VARCHAR(255) NOT NULL,
    amount_value NUMERIC(19,2) NOT NULL,
    amount_currency VARCHAR(10) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    retry_count INT,
    retry_reason VARCHAR(255),
    last_error_message VARCHAR(255)
);

-- payment_orders
CREATE TABLE payment_orders (
    payment_order_id BIGINT PRIMARY KEY,
    public_payment_order_id VARCHAR(255) NOT NULL,
    payment_id BIGINT NOT NULL,
    public_payment_id VARCHAR(255) NOT NULL,
    seller_id VARCHAR(255) NOT NULL,
    amount_value NUMERIC(19,2) NOT NULL,
    amount_currency VARCHAR(10) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    retry_count INT,
    retry_reason VARCHAR(255),
    last_error_message VARCHAR(255),
    CONSTRAINT payment_orders_public_payment_order_id_key UNIQUE (public_payment_order_id)
);

-- payment_order_status_check (kept as is, since not shown in your DB output)
CREATE TABLE payment_order_status_check (
    id BIGSERIAL PRIMARY KEY,
    payment_order_id BIGINT NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    attempt INT NOT NULL,
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
