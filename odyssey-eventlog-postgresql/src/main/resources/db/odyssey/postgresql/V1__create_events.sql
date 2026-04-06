CREATE TABLE IF NOT EXISTS odyssey_events (
    id          BIGSERIAL PRIMARY KEY,
    stream_key  VARCHAR(512) NOT NULL,
    event_type  VARCHAR(256) NOT NULL,
    payload     TEXT NOT NULL,
    timestamp   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata    JSONB DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_odyssey_events_stream_key_id ON odyssey_events (stream_key, id);
