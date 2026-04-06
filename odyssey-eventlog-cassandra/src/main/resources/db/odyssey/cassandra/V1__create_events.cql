CREATE TABLE IF NOT EXISTS odyssey_events (
    stream_key  TEXT,
    event_id    TIMEUUID,
    event_type  TEXT,
    payload     TEXT,
    timestamp   TIMESTAMP,
    metadata    MAP<TEXT, TEXT>,
    PRIMARY KEY (stream_key, event_id)
) WITH CLUSTERING ORDER BY (event_id ASC)
  AND default_time_to_live = 86400;
