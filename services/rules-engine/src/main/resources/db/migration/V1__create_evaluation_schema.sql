-- Care program evaluation results, owned by the rules-engine.
--
-- Each daily run writes a complete result set (patient_program + care_need) under its run_id. The API
-- reads the latest SUCCEEDED run, so a run in progress or a failed run never shows partial results.
-- Old runs are deleted after each successful run (rules-engine.evaluation.keep-runs).

CREATE SCHEMA IF NOT EXISTS eval;

-- Every distinct version of a program file that produced results, kept for auditing
CREATE TABLE eval.program_version (
    program_version_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    program_id   VARCHAR(100) NOT NULL,
    name         VARCHAR(200) NOT NULL,
    short_name   VARCHAR(50),
    source_file  VARCHAR(255) NOT NULL,
    checksum     CHAR(64)     NOT NULL,              -- hex SHA-256 of the file
    definition   TEXT         NOT NULL,              -- the YAML as loaded
    loaded_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_program_version UNIQUE (program_id, checksum),
    CONSTRAINT ck_program_version_checksum CHECK (checksum ~ '^[0-9a-f]{64}$')
);
-- Finds the last good version of a file that now fails validation
CREATE INDEX ix_program_version_source_file ON eval.program_version (source_file, program_version_id DESC);

CREATE TABLE eval.evaluation_run (
    run_id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trigger            VARCHAR(20) NOT NULL,
    as_of_date         DATE        NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    source_etl_run_id  BIGINT,                         -- clinical-data's latest ETL run when this run started
    patients_evaluated INTEGER,
    program_errors     TEXT,                           -- files that failed to load, one per line
    error_message      TEXT,
    started_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at        TIMESTAMPTZ,
    CONSTRAINT ck_evaluation_run_trigger CHECK (trigger IN ('SCHEDULED', 'STARTUP', 'MANUAL')),
    CONSTRAINT ck_evaluation_run_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_evaluation_run_patients CHECK (patients_evaluated IS NULL OR patients_evaluated >= 0)
);
-- Only one run at a time
CREATE UNIQUE INDEX ux_evaluation_run_single_active ON eval.evaluation_run ((TRUE)) WHERE status = 'RUNNING';
CREATE INDEX ix_evaluation_run_status ON eval.evaluation_run (status, run_id DESC);

-- A patient's membership in a program and the tier they were placed in
CREATE TABLE eval.patient_program (
    run_id             BIGINT       NOT NULL REFERENCES eval.evaluation_run (run_id) ON DELETE CASCADE,
    source_patient_id  VARCHAR(64)  NOT NULL,          -- patient_id from patients.csv
    program_id         VARCHAR(100) NOT NULL,
    program_version_id BIGINT       NOT NULL REFERENCES eval.program_version (program_version_id),
    tier_id            VARCHAR(100),                   -- null when eligible but no tier matched
    tier_name          VARCHAR(200),
    evidence           JSONB        NOT NULL,          -- what matched, e.g. {"age": 67}
    PRIMARY KEY (run_id, source_patient_id, program_id)
);
CREATE INDEX ix_patient_program_tier ON eval.patient_program (run_id, program_id, tier_id);
CREATE INDEX ix_patient_program_version ON eval.patient_program (program_version_id);

-- The care gap list: one row per recurring visit a patient needs
CREATE TABLE eval.care_need (
    run_id              BIGINT       NOT NULL,
    source_patient_id   VARCHAR(64)  NOT NULL,
    program_id          VARCHAR(100) NOT NULL,
    specialty           VARCHAR(100) NOT NULL,
    tier_id             VARCHAR(100) NOT NULL,
    every_days          INTEGER      NOT NULL,
    last_visit_date     DATE,                          -- null when never seen
    due_date            DATE         NOT NULL,
    next_scheduled_date DATE,
    status              VARCHAR(20)  NOT NULL,
    priority            VARCHAR(10)  NOT NULL DEFAULT 'normal',
    note                TEXT,
    PRIMARY KEY (run_id, source_patient_id, program_id, specialty),
    FOREIGN KEY (run_id, source_patient_id, program_id)
        REFERENCES eval.patient_program (run_id, source_patient_id, program_id) ON DELETE CASCADE,
    CONSTRAINT ck_care_need_every_days CHECK (every_days > 0),
    CONSTRAINT ck_care_need_status CHECK (status IN ('MET', 'SCHEDULED', 'OVERDUE')),
    CONSTRAINT ck_care_need_priority CHECK (priority IN ('normal', 'high'))
);
CREATE INDEX ix_care_need_status ON eval.care_need (run_id, status, due_date);
CREATE INDEX ix_care_need_program ON eval.care_need (run_id, program_id, tier_id, status);
