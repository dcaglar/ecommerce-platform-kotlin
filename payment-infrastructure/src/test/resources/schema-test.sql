DROP TABLE IF EXISTS outbox_event;

CREATE TABLE outbox_event (
    oeid BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (oeid, created_at)
);

CREATE INDEX idx_outbox_event_status ON outbox_event(status);
CREATE INDEX idx_outbox_event_created_at ON outbox_event(created_at);
