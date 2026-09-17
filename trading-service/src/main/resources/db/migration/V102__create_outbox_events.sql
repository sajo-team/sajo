CREATE TABLE IF NOT EXISTS trading.p_outbox_events (
    id UUID PRIMARY KEY,

    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,

    event_body JSONB NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,

    CONSTRAINT p_outbox_events_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')),

    CONSTRAINT p_outbox_events_retry_count_check
        CHECK (retry_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_pending
    ON trading.p_outbox_events (created_at)
    WHERE status = 'PENDING';