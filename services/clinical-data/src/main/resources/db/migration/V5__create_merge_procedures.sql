-- Promotes the raw staging tables into dbo.
--
-- Entry point, called by the ETL once the four CSVs are in raw and the run is LOADED:
--
--   CALL etl.process_run(<run_id>);
--
-- It merges the lookups first, then patients, then the per-patient facts, and marks the run
-- SUCCEEDED. Everything runs in the caller's transaction, so any error rolls the whole merge back;
-- the ETL then sets the run to FAILED.
--
-- Rows that cannot be promoted (bad dates, unknown patient_id, ...) are skipped and written to
-- etl.load_reject with the reason, rather than failing the run.
--
-- The merges upsert only: rows missing from a later file are not deleted from dbo.
--
-- Each file is upserted on its natural key (enforced by the matching dbo unique constraint):
--
--   patients.csv   (patient_id)                                          -> dbo.patient            uq_patient_source_id
--   diagnoses.csv  (patient_id, icd_code, diagnosed_date)                -> dbo.patient_diagnosis  uq_patient_diagnosis
--   labs.csv       (patient_id, test_name, result_date)                  -> dbo.lab_result         uq_lab_result
--   encounters.csv (patient_id, specialty, encounter_date, provider_name) -> dbo.encounter          uq_encounter
--
-- Key values are compared after whitespace cleanup; test_name, specialty and provider_name match
-- case-insensitively through their lookup tables, and icd_code is upper-cased with the dot restored.
-- When a file has several rows for one key with different non-key values, all of them are rejected
-- rather than one picked at random.

-- ---------------------------------------------------------------------------
-- Reject log
-- ---------------------------------------------------------------------------

CREATE TABLE etl.load_reject (
    reject_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id     BIGINT       NOT NULL REFERENCES etl.load_run (run_id) ON DELETE CASCADE,
    file_name  VARCHAR(100) NOT NULL,
    reason     TEXT         NOT NULL,
    row_data   JSONB        NOT NULL,  -- the row after whitespace cleanup
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_load_reject_run ON etl.load_reject (run_id, file_name);

-- ---------------------------------------------------------------------------
-- Parsing helpers
-- ---------------------------------------------------------------------------

-- Collapses runs of whitespace, trims, and turns empty strings into NULL.
CREATE FUNCTION etl.clean_text(p_value TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$
    SELECT NULLIF(btrim(regexp_replace(p_value, '\s+', ' ', 'g')), '')
$$;

-- YYYY-MM-DD to DATE; NULL when missing, malformed, or not a real date (2024-02-30).
CREATE FUNCTION etl.try_date(p_value TEXT) RETURNS DATE
    LANGUAGE sql STABLE PARALLEL SAFE
AS $$
    SELECT CASE
               WHEN p_value ~ '^\d{4}-\d{2}-\d{2}$' AND pg_input_is_valid(p_value, 'date')
                   THEN p_value::DATE
           END
$$;

-- Plain decimal to NUMERIC; NULL when missing or not a number.
CREATE FUNCTION etl.try_numeric(p_value TEXT) RETURNS NUMERIC
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$
    SELECT CASE WHEN p_value ~ '^[+-]?(\d+(\.\d*)?|\.\d+)$' THEN p_value::NUMERIC END
$$;

-- ---------------------------------------------------------------------------
-- Staging views
--
-- Each view cleans and parses one raw table and exposes:
--   parse_error   : problems with the row itself; lookup merges only read rows where this is NULL
--   reject_reason : parse_error, or a dbo key that could not be resolved; fact merges only read
--                   rows where this is NULL, and rows where it is set go to etl.load_reject
-- Exact duplicate rows are collapsed before validation.
-- ---------------------------------------------------------------------------

CREATE VIEW etl.v_patients AS
WITH cleaned AS (
    SELECT DISTINCT
        etl.clean_text(patient_id)        AS source_patient_id,
        etl.clean_text(first_name)        AS first_name,
        etl.clean_text(last_name)         AS last_name,
        etl.clean_text(date_of_birth)     AS date_of_birth_text,
        upper(etl.clean_text(gender))     AS gender,
        etl.clean_text(phone)             AS phone,
        etl.clean_text(language)          AS language,
        etl.clean_text(pcp_provider_name) AS pcp_provider_name
    FROM raw.patients
),
parsed AS (
    SELECT c.*,
           etl.try_date(c.date_of_birth_text)                  AS date_of_birth,
           count(*) OVER (PARTITION BY c.source_patient_id)    AS id_count
    FROM cleaned c
),
validated AS (
    SELECT p.*,
           CASE
               WHEN p.source_patient_id IS NULL        THEN 'patient_id is missing'
               WHEN length(p.source_patient_id) > 64   THEN 'patient_id is longer than 64 characters'
               WHEN p.id_count > 1                     THEN 'patient_id appears more than once with different values'
               WHEN p.first_name IS NULL               THEN 'first_name is missing'
               WHEN length(p.first_name) > 100         THEN 'first_name is longer than 100 characters'
               WHEN p.last_name IS NULL                THEN 'last_name is missing'
               WHEN length(p.last_name) > 100          THEN 'last_name is longer than 100 characters'
               WHEN p.date_of_birth_text IS NULL       THEN 'date_of_birth is missing'
               WHEN p.date_of_birth IS NULL            THEN 'date_of_birth is not a valid YYYY-MM-DD date'
               WHEN p.date_of_birth < DATE '1900-01-01'
                 OR p.date_of_birth > current_date     THEN 'date_of_birth is out of range'
               WHEN p.gender IS NULL
                 OR p.gender NOT IN ('M', 'F')         THEN 'gender must be M or F'
               WHEN length(p.phone) > 32               THEN 'phone is longer than 32 characters'
               WHEN length(p.language) > 100           THEN 'language is longer than 100 characters'
               WHEN length(p.pcp_provider_name) > 200  THEN 'pcp_provider_name is longer than 200 characters'
           END AS parse_error
    FROM parsed p
)
SELECT v.source_patient_id,
       v.first_name,
       v.last_name,
       v.date_of_birth,
       v.gender,
       v.phone,
       v.language,
       v.pcp_provider_name,
       l.language_id,
       pr.provider_id AS pcp_provider_id,
       v.parse_error,
       v.parse_error  AS reject_reason,
       jsonb_build_object(
           'patient_id',        v.source_patient_id,
           'first_name',        v.first_name,
           'last_name',         v.last_name,
           'date_of_birth',     v.date_of_birth_text,
           'gender',            v.gender,
           'phone',             v.phone,
           'language',          v.language,
           'pcp_provider_name', v.pcp_provider_name) AS row_data
FROM validated v
LEFT JOIN dbo.language l  ON lower(l.language_name)  = lower(v.language)
LEFT JOIN dbo.provider pr ON lower(pr.provider_name) = lower(v.pcp_provider_name);

CREATE VIEW etl.v_diagnoses AS
WITH cleaned AS (
    SELECT DISTINCT
        etl.clean_text(patient_id)                                AS source_patient_id,
        upper(NULLIF(regexp_replace(icd_code, '\s', '', 'g'), '')) AS icd_code_text,
        etl.clean_text(description)                               AS description,
        etl.clean_text(diagnosed_date)                            AS diagnosed_date_text
    FROM raw.diagnoses
),
parsed AS (
    SELECT c.*,
           CASE
               WHEN c.icd_code_text ~ '^[A-Z][0-9]{2}(\.[0-9A-Z]{1,4})?$' THEN c.icd_code_text
               -- accept codes sent without the dot: E1165 -> E11.65
               WHEN c.icd_code_text ~ '^[A-Z][0-9]{2}[0-9A-Z]{1,4}$'
                   THEN left(c.icd_code_text, 3) || '.' || substr(c.icd_code_text, 4)
           END                                       AS icd_code,
           etl.try_date(c.diagnosed_date_text)       AS diagnosed_date
    FROM cleaned c
),
windowed AS (
    SELECT p.*,
           min(p.description) OVER w AS min_description,
           max(p.description) OVER w AS max_description
    FROM parsed p
    WINDOW w AS (PARTITION BY p.source_patient_id, p.icd_code, p.diagnosed_date)
),
validated AS (
    SELECT w.*,
           CASE
               WHEN w.source_patient_id IS NULL    THEN 'patient_id is missing'
               WHEN w.icd_code_text IS NULL        THEN 'icd_code is missing'
               WHEN w.icd_code IS NULL             THEN 'icd_code is not a valid ICD-10 code'
               WHEN w.diagnosed_date_text IS NULL  THEN 'diagnosed_date is missing'
               WHEN w.diagnosed_date IS NULL       THEN 'diagnosed_date is not a valid YYYY-MM-DD date'
               WHEN length(w.description) > 255    THEN 'description is longer than 255 characters'
               WHEN w.min_description <> w.max_description
                                                   THEN 'conflicting description for the same patient, icd_code and diagnosed_date'
           END AS parse_error
    FROM windowed w
)
SELECT v.source_patient_id,
       v.icd_code,
       v.description,
       v.diagnosed_date,
       pt.patient_id,
       v.parse_error,
       COALESCE(v.parse_error,
                CASE
                    WHEN pt.patient_id IS NULL THEN 'patient_id not found in patients'
                    WHEN dc.icd_code IS NULL   THEN 'icd_code has no description'
                END) AS reject_reason,
       jsonb_build_object(
           'patient_id',     v.source_patient_id,
           'icd_code',       v.icd_code_text,
           'description',    v.description,
           'diagnosed_date', v.diagnosed_date_text) AS row_data
FROM validated v
LEFT JOIN dbo.patient        pt ON pt.source_patient_id = v.source_patient_id
LEFT JOIN dbo.diagnosis_code dc ON dc.icd_code = v.icd_code;

CREATE VIEW etl.v_labs AS
WITH cleaned AS (
    SELECT DISTINCT
        etl.clean_text(patient_id)   AS source_patient_id,
        etl.clean_text(test_name)    AS test_name,
        etl.clean_text(result_value) AS result_value_text,
        etl.clean_text(result_date)  AS result_date_text
    FROM raw.labs
),
parsed AS (
    SELECT c.*,
           -- rounded to the dbo.lab_result.result_value scale so equal stored values compare equal
           round(etl.try_numeric(c.result_value_text), 4) AS result_value,
           etl.try_date(c.result_date_text)               AS result_date
    FROM cleaned c
),
windowed AS (
    SELECT p.*,
           min(p.result_value) OVER w AS min_value,
           max(p.result_value) OVER w AS max_value
    FROM parsed p
    WINDOW w AS (PARTITION BY p.source_patient_id, lower(p.test_name), p.result_date)
),
validated AS (
    SELECT w.*,
           CASE
               WHEN w.source_patient_id IS NULL   THEN 'patient_id is missing'
               WHEN w.test_name IS NULL           THEN 'test_name is missing'
               WHEN length(w.test_name) > 100     THEN 'test_name is longer than 100 characters'
               WHEN w.result_value_text IS NULL   THEN 'result_value is missing'
               WHEN w.result_value IS NULL        THEN 'result_value is not a number'
               WHEN abs(w.result_value) >= 1e8    THEN 'result_value is out of range'
               WHEN w.result_date_text IS NULL    THEN 'result_date is missing'
               WHEN w.result_date IS NULL         THEN 'result_date is not a valid YYYY-MM-DD date'
               WHEN w.min_value <> w.max_value    THEN 'conflicting result_value for the same patient, test and date'
           END AS parse_error
    FROM windowed w
)
SELECT v.source_patient_id,
       v.test_name,
       v.result_value,
       v.result_date,
       pt.patient_id,
       lt.lab_test_id,
       v.parse_error,
       COALESCE(v.parse_error,
                CASE
                    WHEN pt.patient_id IS NULL  THEN 'patient_id not found in patients'
                    WHEN lt.lab_test_id IS NULL THEN 'test_name not found in lab tests'
                END) AS reject_reason,
       jsonb_build_object(
           'patient_id',   v.source_patient_id,
           'test_name',    v.test_name,
           'result_value', v.result_value_text,
           'result_date',  v.result_date_text) AS row_data
FROM validated v
LEFT JOIN dbo.patient  pt ON pt.source_patient_id = v.source_patient_id
LEFT JOIN dbo.lab_test lt ON lower(lt.test_name)  = lower(v.test_name);

CREATE VIEW etl.v_encounters AS
WITH cleaned AS (
    SELECT DISTINCT
        etl.clean_text(patient_id)     AS source_patient_id,
        etl.clean_text(specialty)      AS specialty,
        etl.clean_text(encounter_date) AS encounter_date_text,
        etl.clean_text(provider_name)  AS provider_name
    FROM raw.encounters
),
parsed AS (
    SELECT c.*,
           etl.try_date(c.encounter_date_text) AS encounter_date
    FROM cleaned c
),
validated AS (
    SELECT p.*,
           CASE
               WHEN p.source_patient_id IS NULL    THEN 'patient_id is missing'
               WHEN p.specialty IS NULL            THEN 'specialty is missing'
               WHEN length(p.specialty) > 100      THEN 'specialty is longer than 100 characters'
               WHEN p.encounter_date_text IS NULL  THEN 'encounter_date is missing'
               WHEN p.encounter_date IS NULL       THEN 'encounter_date is not a valid YYYY-MM-DD date'
               WHEN length(p.provider_name) > 200  THEN 'provider_name is longer than 200 characters'
           END AS parse_error
    FROM parsed p
)
SELECT v.source_patient_id,
       v.specialty,
       v.encounter_date,
       v.provider_name,
       pt.patient_id,
       sp.specialty_id,
       pr.provider_id,
       v.parse_error,
       COALESCE(v.parse_error,
                CASE
                    WHEN pt.patient_id IS NULL    THEN 'patient_id not found in patients'
                    WHEN sp.specialty_id IS NULL  THEN 'specialty not found in specialties'
                    WHEN v.provider_name IS NOT NULL
                     AND pr.provider_id IS NULL   THEN 'provider_name not found in providers'
                END) AS reject_reason,
       jsonb_build_object(
           'patient_id',     v.source_patient_id,
           'specialty',      v.specialty,
           'encounter_date', v.encounter_date_text,
           'provider_name',  v.provider_name) AS row_data
FROM validated v
LEFT JOIN dbo.patient   pt ON pt.source_patient_id  = v.source_patient_id
LEFT JOIN dbo.specialty sp ON lower(sp.specialty_name) = lower(v.specialty)
LEFT JOIN dbo.provider  pr ON lower(pr.provider_name)  = lower(v.provider_name);

-- ---------------------------------------------------------------------------
-- Lookup merges (insert-only; names match case-insensitively).
-- A new name is stored with its most common spelling in the file, capitalized forms winning ties.
-- ---------------------------------------------------------------------------

CREATE PROCEDURE etl.merge_language()
    LANGUAGE plpgsql
AS $$
DECLARE
    v_rows BIGINT;
BEGIN
    MERGE INTO dbo.language t
    USING (
        SELECT DISTINCT ON (lower(language)) language
        FROM etl.v_patients
        WHERE parse_error IS NULL AND language IS NOT NULL
        GROUP BY language
        ORDER BY lower(language), count(*) DESC, language COLLATE "C"
    ) s
    ON lower(t.language_name) = lower(s.language)
    WHEN NOT MATCHED THEN
        INSERT (language_name) VALUES (s.language);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    RAISE NOTICE 'dbo.language: % rows merged', v_rows;
END;
$$;

CREATE PROCEDURE etl.merge_specialty()
    LANGUAGE plpgsql
AS $$
DECLARE
    v_rows BIGINT;
BEGIN
    MERGE INTO dbo.specialty t
    USING (
        SELECT DISTINCT ON (lower(specialty)) specialty
        FROM etl.v_encounters
        WHERE parse_error IS NULL
        GROUP BY specialty
        ORDER BY lower(specialty), count(*) DESC, specialty COLLATE "C"
    ) s
    ON lower(t.specialty_name) = lower(s.specialty)
    WHEN NOT MATCHED THEN
        INSERT (specialty_name) VALUES (s.specialty);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    RAISE NOTICE 'dbo.specialty: % rows merged', v_rows;
END;
$$;

-- Providers come from both the patients' PCP and the encounters.
CREATE PROCEDURE etl.merge_provider()
    LANGUAGE plpgsql
AS $$
DECLARE
    v_rows BIGINT;
BEGIN
    MERGE INTO dbo.provider t
    USING (
        SELECT DISTINCT ON (lower(provider_name)) provider_name
        FROM (
            SELECT pcp_provider_name AS provider_name
            FROM etl.v_patients
            WHERE parse_error IS NULL AND pcp_provider_name IS NOT NULL
            UNION ALL
            SELECT provider_name
            FROM etl.v_encounters
            WHERE parse_error IS NULL AND provider_name IS NOT NULL
        ) names
        GROUP BY provider_name
        ORDER BY lower(provider_name), count(*) DESC, provider_name COLLATE "C"
    ) s
    ON lower(t.provider_name) = lower(s.provider_name)
    WHEN NOT MATCHED THEN
        INSERT (provider_name) VALUES (s.provider_name);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    RAISE NOTICE 'dbo.provider: % rows merged', v_rows;
END;
$$;

CREATE PROCEDURE etl.merge_lab_test()
    LANGUAGE plpgsql
AS $$
DECLARE
    v_rows BIGINT;
BEGIN
    MERGE INTO dbo.lab_test t
    USING (
        SELECT DISTINCT ON (lower(test_name)) test_name
        FROM etl.v_labs
        WHERE parse_error IS NULL
        GROUP BY test_name
        ORDER BY lower(test_name), count(*) DESC, test_name COLLATE "C"
    ) s
    ON lower(t.test_name) = lower(s.test_name)
    WHEN NOT MATCHED THEN
        INSERT (test_name) VALUES (s.test_name);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    RAISE NOTICE 'dbo.lab_test: % rows merged', v_rows;
END;
$$;

-- Upserts codes and their descriptions. When a code appears with several descriptions, the one on
-- the most recent diagnosis wins. The condition group is the longest matching ICD prefix: E11
-- matches E11 and E11.65, G47.3 matches G47.33.
CREATE PROCEDURE etl.merge_diagnosis_code()
    LANGUAGE plpgsql
AS $$
DECLARE
    v_rows BIGINT;
BEGIN
    MERGE INTO dbo.diagnosis_code t
    USING (
        SELECT d.icd_code,
               d.description,
               (SELECT cg.condition_group_id
                FROM dbo.condition_group cg
                WHERE d.icd_code = cg.icd_prefix
                   OR d.icd_code LIKE cg.icd_prefix
                                      || CASE WHEN strpos(cg.icd_prefix, '.') > 0 THEN '%' ELSE '.%' END
                ORDER BY length(cg.icd_prefix) DESC
                LIMIT 1) AS condition_group_id
        FROM (
            SELECT DISTINCT ON (icd_code) icd_code, description
            FROM etl.v_diagnoses
            WHERE parse_error IS NULL AND description IS NOT NULL
            ORDER BY icd_code, diagnosed_date DESC, description
        ) d
    ) s
    ON t.icd_code = s.icd_code
    WHEN MATCHED AND (t.description, t.condition_group_id)
                     IS DISTINCT FROM (s.description, s.condition_group_id) THEN
        UPDATE SET description        = s.description,
                   condition_group_id = s.condition_group_id
    WHEN NOT MATCHED THEN
        INSERT (icd_code, description, condition_group_id)
        VALUES (s.icd_code, s.description, s.condition_group_id);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    RAISE NOTICE 'dbo.diagnosis_code: % rows merged', v_rows;
END;
$$;

-- ---------------------------------------------------------------------------
-- Patient and fact merges (log rejects, then upsert)
-- ---------------------------------------------------------------------------

CREATE PROCEDURE etl.merge_patient(p_run_id BIGINT)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_rows BIGINT;
BEGIN
    INSERT INTO etl.load_reject (run_id, file_name, reason, row_data)
    SELECT p_run_id, 'patients.csv', reject_reason, row_data
    FROM etl.v_patients
    WHERE reject_reason IS NOT NULL;

    MERGE INTO dbo.patient t
    USING (
        SELECT * FROM etl.v_patients WHERE reject_reason IS NULL
    ) s
    ON t.source_patient_id = s.source_patient_id
    WHEN MATCHED AND (t.first_name, t.last_name, t.date_of_birth, t.gender, t.phone,
                      t.language_id, t.pcp_provider_id)
                     IS DISTINCT FROM
                     (s.first_name, s.last_name, s.date_of_birth, s.gender, s.phone,
                      s.language_id, s.pcp_provider_id) THEN
        UPDATE SET first_name      = s.first_name,
                   last_name       = s.last_name,
                   date_of_birth   = s.date_of_birth,
                   gender          = s.gender,
                   phone           = s.phone,
                   language_id     = s.language_id,
                   pcp_provider_id = s.pcp_provider_id,
                   updated_at      = now()
    WHEN NOT MATCHED THEN
        INSERT (source_patient_id, first_name, last_name, date_of_birth, gender, phone,
                language_id, pcp_provider_id)
        VALUES (s.source_patient_id, s.first_name, s.last_name, s.date_of_birth, s.gender, s.phone,
                s.language_id, s.pcp_provider_id);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    RAISE NOTICE 'dbo.patient: % rows merged', v_rows;
END;
$$;

CREATE PROCEDURE etl.merge_patient_diagnosis(p_run_id BIGINT)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_rows BIGINT;
BEGIN
    INSERT INTO etl.load_reject (run_id, file_name, reason, row_data)
    SELECT p_run_id, 'diagnoses.csv', reject_reason, row_data
    FROM etl.v_diagnoses
    WHERE reject_reason IS NOT NULL;

    MERGE INTO dbo.patient_diagnosis t
    USING (
        SELECT DISTINCT patient_id, icd_code, diagnosed_date
        FROM etl.v_diagnoses
        WHERE reject_reason IS NULL
    ) s
    ON  t.patient_id     = s.patient_id
    AND t.icd_code       = s.icd_code
    AND t.diagnosed_date = s.diagnosed_date
    WHEN NOT MATCHED THEN
        INSERT (patient_id, icd_code, diagnosed_date)
        VALUES (s.patient_id, s.icd_code, s.diagnosed_date);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    RAISE NOTICE 'dbo.patient_diagnosis: % rows merged', v_rows;
END;
$$;

CREATE PROCEDURE etl.merge_lab_result(p_run_id BIGINT)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_rows BIGINT;
BEGIN
    INSERT INTO etl.load_reject (run_id, file_name, reason, row_data)
    SELECT p_run_id, 'labs.csv', reject_reason, row_data
    FROM etl.v_labs
    WHERE reject_reason IS NOT NULL;

    MERGE INTO dbo.lab_result t
    USING (
        SELECT DISTINCT patient_id, lab_test_id, result_date, result_value
        FROM etl.v_labs
        WHERE reject_reason IS NULL
    ) s
    ON  t.patient_id  = s.patient_id
    AND t.lab_test_id = s.lab_test_id
    AND t.result_date = s.result_date
    WHEN MATCHED AND t.result_value <> s.result_value THEN
        UPDATE SET result_value = s.result_value
    WHEN NOT MATCHED THEN
        INSERT (patient_id, lab_test_id, result_value, result_date)
        VALUES (s.patient_id, s.lab_test_id, s.result_value, s.result_date);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    RAISE NOTICE 'dbo.lab_result: % rows merged', v_rows;
END;
$$;

CREATE PROCEDURE etl.merge_encounter(p_run_id BIGINT)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_rows BIGINT;
BEGIN
    INSERT INTO etl.load_reject (run_id, file_name, reason, row_data)
    SELECT p_run_id, 'encounters.csv', reject_reason, row_data
    FROM etl.v_encounters
    WHERE reject_reason IS NOT NULL;

    MERGE INTO dbo.encounter t
    USING (
        SELECT DISTINCT patient_id, specialty_id, provider_id, encounter_date
        FROM etl.v_encounters
        WHERE reject_reason IS NULL
    ) s
    ON  t.patient_id     = s.patient_id
    AND t.specialty_id   = s.specialty_id
    AND t.provider_id IS NOT DISTINCT FROM s.provider_id
    AND t.encounter_date = s.encounter_date
    WHEN NOT MATCHED THEN
        INSERT (patient_id, specialty_id, provider_id, encounter_date)
        VALUES (s.patient_id, s.specialty_id, s.provider_id, s.encounter_date);

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    RAISE NOTICE 'dbo.encounter: % rows merged', v_rows;
END;
$$;

-- ---------------------------------------------------------------------------
-- Entry point
-- ---------------------------------------------------------------------------

CREATE PROCEDURE etl.process_run(p_run_id BIGINT)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_status  etl.load_run.status%TYPE;
    v_rejects BIGINT;
BEGIN
    SELECT status INTO v_status
    FROM etl.load_run
    WHERE run_id = p_run_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'etl.load_run % does not exist', p_run_id;
    END IF;
    IF v_status <> 'LOADED' THEN
        RAISE EXCEPTION 'etl.load_run % is %, expected LOADED', p_run_id, v_status;
    END IF;

    -- Lookups first: the patient and fact views resolve their keys against them.
    CALL etl.merge_language();
    CALL etl.merge_specialty();
    CALL etl.merge_provider();
    CALL etl.merge_lab_test();
    CALL etl.merge_diagnosis_code();

    CALL etl.merge_patient(p_run_id);

    CALL etl.merge_patient_diagnosis(p_run_id);
    CALL etl.merge_lab_result(p_run_id);
    CALL etl.merge_encounter(p_run_id);

    SELECT count(*) INTO v_rejects FROM etl.load_reject WHERE run_id = p_run_id;
    RAISE NOTICE 'run %: % rows rejected, see etl.load_reject', p_run_id, v_rejects;

    UPDATE etl.load_run
    SET status = 'SUCCEEDED', finished_at = now()
    WHERE run_id = p_run_id;
END;
$$;
