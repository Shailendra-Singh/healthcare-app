-- Batch tracking for ingestion. Unqualified names: Flyway runs this with the
-- "ingestion" schema as default (quarkus.flyway.schemas).

-- One row per poll that found at least one new file.
CREATE TABLE batch (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id     UUID        NOT NULL UNIQUE,
    status       TEXT        NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT batch_status_check
        CHECK (status IN ('RECEIVED', 'PUBLISHED', 'FAILED'))
);

-- One row per new file copied into a batch. Files whose checksum matches a
-- LOADED file are skipped and never recorded.
CREATE TABLE batch_file (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_ref    BIGINT   NOT NULL REFERENCES batch (id),
    file_type    TEXT     NOT NULL,
    sha256       CHAR(64) NOT NULL,
    storage_path TEXT     NOT NULL,   -- relative to the storage root
    status       TEXT     NOT NULL,
    CONSTRAINT batch_file_type_check
        CHECK (file_type IN ('PATIENTS', 'DIAGNOSES', 'LABS', 'ENCOUNTERS')),
    CONSTRAINT batch_file_status_check
        CHECK (status IN ('NEW', 'LOADED')),
    CONSTRAINT batch_file_one_per_type
        UNIQUE (batch_ref, file_type)
);

-- "Already loaded?" lookup: only LOADED rows count.
CREATE INDEX batch_file_loaded_checksum_idx
    ON batch_file (file_type, sha256)
    WHERE status = 'LOADED';
