-- Staging tables mirroring the CSV files one-to-one.
-- They are truncated and reloaded on every run, then promoted into dbo by a stored procedure,
-- so they carry no keys, constraints or indexes. Every column is TEXT and nullable so a
-- malformed row still loads; type conversion and validation happen during promotion.

CREATE TABLE raw.patients (
    patient_id        TEXT,
    first_name        TEXT,
    last_name         TEXT,
    date_of_birth     TEXT,
    gender            TEXT,
    phone             TEXT,
    language          TEXT,
    pcp_provider_name TEXT
);

CREATE TABLE raw.diagnoses (
    patient_id     TEXT,
    icd_code       TEXT,
    description    TEXT,
    diagnosed_date TEXT
);

CREATE TABLE raw.labs (
    patient_id   TEXT,
    test_name    TEXT,
    result_value TEXT,
    result_date  TEXT
);

CREATE TABLE raw.encounters (
    patient_id     TEXT,
    specialty      TEXT,
    encounter_date TEXT,
    provider_name  TEXT
);
