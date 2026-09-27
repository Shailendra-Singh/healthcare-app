-- etl: load bookkeeping, raw: CSV staging, dbo: normalized model
CREATE SCHEMA IF NOT EXISTS etl;
CREATE SCHEMA IF NOT EXISTS raw;
CREATE SCHEMA IF NOT EXISTS dbo;

-- One row per ETL run. A run covers all four CSV files:
--   STARTED   -> raw tables truncated and being loaded
--   LOADED    -> raw tables loaded, stored procedure merging into dbo
--   SUCCEEDED -> dbo merged
--   FAILED    -> error_message says why
--   SKIPPED   -> checksums matched the last SUCCEEDED run; nothing loaded
CREATE TABLE etl.load_run (
    run_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status        VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    error_message TEXT,
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ,
    CONSTRAINT ck_load_run_status CHECK (status IN ('STARTED', 'LOADED', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_load_run_finished CHECK (finished_at IS NULL OR finished_at >= started_at)
);

-- Only one run may be in flight at a time; a second concurrent run fails on insert.
CREATE UNIQUE INDEX ux_load_run_single_active ON etl.load_run ((TRUE))
    WHERE status IN ('STARTED', 'LOADED');

-- Finds the last SUCCEEDED run, whose checksums decide whether to skip.
CREATE INDEX ix_load_run_status_started ON etl.load_run (status, started_at DESC);

-- One row per CSV file per run.
CREATE TABLE etl.load_file (
    run_id     BIGINT       NOT NULL REFERENCES etl.load_run (run_id) ON DELETE CASCADE,
    file_name  VARCHAR(100) NOT NULL,
    checksum   CHAR(64)     NOT NULL,  -- hex SHA-256 of the file contents
    file_size  BIGINT       NOT NULL,
    row_count  INTEGER,                -- set once the file is copied into raw
    CONSTRAINT pk_load_file PRIMARY KEY (run_id, file_name),
    CONSTRAINT ck_load_file_name CHECK (file_name IN ('patients.csv', 'diagnoses.csv', 'labs.csv', 'encounters.csv')),
    CONSTRAINT ck_load_file_checksum CHECK (checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_load_file_size CHECK (file_size >= 0),
    CONSTRAINT ck_load_file_row_count CHECK (row_count IS NULL OR row_count >= 0)
);
