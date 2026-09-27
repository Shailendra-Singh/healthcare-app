-- Normalized model built from the raw CSV tables.
--
--   language, specialty, provider, lab_test  : lookups de-duplicated from free-text columns
--   condition_group                          : ICD-10 families (chronic condition rules)
--   diagnosis_code                           : ICD-10 code + description (was repeated per diagnosis row)
--   patient                                  : one row per source patient_id
--   patient_diagnosis, lab_result, encounter : facts keyed to patient
--
-- PostgreSQL does not index foreign key columns automatically, so each FK gets an index
-- unless it is already the leading column of a unique constraint.

-- ---------------------------------------------------------------------------
-- Lookups
-- ---------------------------------------------------------------------------

CREATE TABLE dbo.language (
    language_id   SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    language_name VARCHAR(100) NOT NULL,
    CONSTRAINT ck_language_name_not_blank CHECK (btrim(language_name) <> '')
);
CREATE UNIQUE INDEX ux_language_name ON dbo.language (lower(language_name));

CREATE TABLE dbo.specialty (
    specialty_id   SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    specialty_name VARCHAR(100) NOT NULL,
    CONSTRAINT ck_specialty_name_not_blank CHECK (btrim(specialty_name) <> '')
);
CREATE UNIQUE INDEX ux_specialty_name ON dbo.specialty (lower(specialty_name));

CREATE TABLE dbo.provider (
    provider_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_name VARCHAR(200) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_provider_name_not_blank CHECK (btrim(provider_name) <> '')
);
CREATE UNIQUE INDEX ux_provider_name ON dbo.provider (lower(provider_name));

CREATE TABLE dbo.lab_test (
    lab_test_id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    test_name   VARCHAR(100) NOT NULL,
    CONSTRAINT ck_lab_test_name_not_blank CHECK (btrim(test_name) <> '')
);
CREATE UNIQUE INDEX ux_lab_test_name ON dbo.lab_test (lower(test_name));

-- ICD-10 code families, e.g. E11 = Type 2 Diabetes, G47.3 = Sleep Apnea.
CREATE TABLE dbo.condition_group (
    condition_group_id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    icd_prefix         VARCHAR(8)   NOT NULL,
    condition_name     VARCHAR(100) NOT NULL,
    is_chronic         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_condition_group_prefix UNIQUE (icd_prefix),
    CONSTRAINT ck_condition_group_prefix CHECK (icd_prefix ~ '^[A-Z][0-9]{2}(\.[0-9A-Z]{1,4})?$')
);

CREATE TABLE dbo.diagnosis_code (
    icd_code           VARCHAR(8)   PRIMARY KEY,
    description        VARCHAR(255) NOT NULL,
    condition_group_id SMALLINT REFERENCES dbo.condition_group (condition_group_id),
    -- ICD-10-CM: letter, two digits, optional dot and up to four more characters (E11.65, I10)
    CONSTRAINT ck_diagnosis_code_format CHECK (icd_code ~ '^[A-Z][0-9]{2}(\.[0-9A-Z]{1,4})?$')
);
CREATE INDEX ix_diagnosis_code_group ON dbo.diagnosis_code (condition_group_id);

-- ---------------------------------------------------------------------------
-- Patient
-- ---------------------------------------------------------------------------

CREATE TABLE dbo.patient (
    patient_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_patient_id VARCHAR(64)  NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    date_of_birth     DATE         NOT NULL,
    gender            CHAR(1)      NOT NULL,
    phone             VARCHAR(32),
    language_id       SMALLINT REFERENCES dbo.language (language_id),
    pcp_provider_id   BIGINT REFERENCES dbo.provider (provider_id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_patient_source_id UNIQUE (source_patient_id),
    CONSTRAINT ck_patient_gender CHECK (gender IN ('M', 'F')),
    CONSTRAINT ck_patient_dob CHECK (date_of_birth >= DATE '1900-01-01'),
    CONSTRAINT ck_patient_first_name_not_blank CHECK (btrim(first_name) <> ''),
    CONSTRAINT ck_patient_last_name_not_blank CHECK (btrim(last_name) <> '')
);
CREATE INDEX ix_patient_name     ON dbo.patient (lower(last_name), lower(first_name));
CREATE INDEX ix_patient_dob      ON dbo.patient (date_of_birth);
CREATE INDEX ix_patient_language ON dbo.patient (language_id);
CREATE INDEX ix_patient_pcp      ON dbo.patient (pcp_provider_id);

-- ---------------------------------------------------------------------------
-- Clinical facts
-- ---------------------------------------------------------------------------

CREATE TABLE dbo.patient_diagnosis (
    patient_diagnosis_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id           BIGINT      NOT NULL REFERENCES dbo.patient (patient_id) ON DELETE CASCADE,
    icd_code             VARCHAR(8)  NOT NULL REFERENCES dbo.diagnosis_code (icd_code),
    diagnosed_date       DATE        NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_patient_diagnosis UNIQUE (patient_id, icd_code, diagnosed_date)
);
CREATE INDEX ix_patient_diagnosis_code ON dbo.patient_diagnosis (icd_code, patient_id);

CREATE TABLE dbo.lab_result (
    lab_result_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id    BIGINT        NOT NULL REFERENCES dbo.patient (patient_id) ON DELETE CASCADE,
    lab_test_id   SMALLINT      NOT NULL REFERENCES dbo.lab_test (lab_test_id),
    result_value  NUMERIC(12,4) NOT NULL,
    result_date   DATE          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_lab_result UNIQUE (patient_id, lab_test_id, result_date)
);
-- "Latest result of test X for patient" is served by uq_lab_result; this covers cohort queries by test.
CREATE INDEX ix_lab_result_test_date ON dbo.lab_result (lab_test_id, result_date DESC);

-- Includes both completed visits and future scheduled appointments;
-- past vs. upcoming is derived from encounter_date rather than stored.
CREATE TABLE dbo.encounter (
    encounter_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id     BIGINT      NOT NULL REFERENCES dbo.patient (patient_id) ON DELETE CASCADE,
    specialty_id   SMALLINT    NOT NULL REFERENCES dbo.specialty (specialty_id),
    provider_id    BIGINT REFERENCES dbo.provider (provider_id),
    encounter_date DATE        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_encounter UNIQUE NULLS NOT DISTINCT (patient_id, specialty_id, provider_id, encounter_date)
);
CREATE INDEX ix_encounter_patient_date   ON dbo.encounter (patient_id, encounter_date DESC);
CREATE INDEX ix_encounter_specialty_date ON dbo.encounter (specialty_id, encounter_date);
CREATE INDEX ix_encounter_provider       ON dbo.encounter (provider_id);
