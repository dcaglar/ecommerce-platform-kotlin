DROP TABLE IF EXISTS outbox_event;

-- Outbox Event Table for Testing (H2 compatible)
CREATE TABLE outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Add indexes if needed for test performance
CREATE INDEX idx_outbox_event_status ON outbox_event(status);
CREATE INDEX idx_outbox_event_created_at ON outbox_event(created_at);
