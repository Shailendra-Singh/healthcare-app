-- The ETL's last check of the data folder. Runs are only recorded when files are loaded, so this
-- single row is what shows the ETL is still alive while the files stay unchanged.
CREATE TABLE etl.heartbeat (
    heartbeat_id     SMALLINT     PRIMARY KEY DEFAULT 1,
    checked_at       TIMESTAMPTZ  NOT NULL,
    outcome          VARCHAR(20)  NOT NULL,
    detail           TEXT,
    interval_seconds INTEGER      NOT NULL,
    CONSTRAINT ck_heartbeat_single_row CHECK (heartbeat_id = 1),
    CONSTRAINT ck_heartbeat_outcome CHECK (outcome IN ('waiting', 'unchanged', 'loaded', 'failed', 'busy')),
    CONSTRAINT ck_heartbeat_interval CHECK (interval_seconds > 0)
);
