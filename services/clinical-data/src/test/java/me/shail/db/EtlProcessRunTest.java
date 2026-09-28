package me.shail.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import me.shail.support.TestData;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the V5 merge procedure the ETL calls after loading the four CSVs into the raw tables:
 * validation, rejects, idempotent re-runs and upserts on each file's natural key.
 */
@QuarkusTest
class EtlProcessRunTest {

    @Inject
    TestDatabase db;

    // Generated once per test, so running loadValidFiles twice loads identical files
    String languageName;
    String pcpName;
    String encounterProviderName;
    List<String> patientIds;
    List<String> firstNames;
    List<String> lastNames;

    @BeforeEach
    void reset() {
        db.reset();
        languageName = TestData.FAKER.nation().language();
        pcpName = TestData.providerName();
        encounterProviderName = TestData.providerName();
        patientIds = List.of(TestData.sourcePatientId(), TestData.sourcePatientId(), TestData.sourcePatientId());
        firstNames = List.of(TestData.FAKER.name().firstName(), TestData.FAKER.name().firstName(),
                TestData.FAKER.name().firstName());
        lastNames = List.of(TestData.FAKER.name().lastName(), TestData.FAKER.name().lastName(),
                TestData.FAKER.name().lastName());
    }

    @Test
    void mergesValidRowsIntoEveryDboTable() {
        long runId = loadValidFiles("555-0100", "8.1");

        process(runId);

        assertEquals("SUCCEEDED", status(runId));
        assertEquals(0, count("etl.load_reject"));
        assertEquals(3, count("dbo.patient"));
        assertEquals(1, count("dbo.language"));
        assertEquals(2, count("dbo.provider"), "PCP and the encounter provider");
        assertEquals(2, count("dbo.specialty"));
        assertEquals(1, count("dbo.lab_test"));
        assertEquals(2, count("dbo.diagnosis_code"));
        assertEquals(3, count("dbo.patient_diagnosis"));
        assertEquals(2, count("dbo.lab_result"));
        assertEquals(2, count("dbo.encounter"));
        assertEquals("E11", db.queryForString("""
                SELECT g.icd_prefix FROM dbo.diagnosis_code d JOIN dbo.condition_group g USING (condition_group_id)
                WHERE d.icd_code = 'E11.65'"""));
    }

    @Test
    void normalizesCodesWrittenWithoutTheDot() {
        long runId = startRun();
        rawPatient(patientIds.get(0), "1970-03-12", "F", "555-0100");
        rawDiagnosis(patientIds.get(0), "e1165", "Type 2 diabetes with hyperglycemia", "2020-01-01");
        markLoaded(runId);

        process(runId);

        assertEquals("E11.65", db.queryForString("SELECT icd_code FROM dbo.patient_diagnosis"));
    }

    @Test
    void rejectsInvalidRowsAndLoadsTheRest() {
        long runId = startRun();
        rawPatient(patientIds.get(0), "1970-03-12", "F", "555-0100");
        rawPatient(patientIds.get(1), "1985-07-01", "X", "555-0101");
        rawPatient(patientIds.get(2), "1990-02-30", "M", "555-0102");
        rawLab(patientIds.get(0), "HbA1c", "not a number", "2024-01-10");
        rawLab("P-UNKNOWN", "HbA1c", "7.2", "2024-01-10");
        rawDiagnosis(patientIds.get(0), "BAD", "Nope", "2020-01-01");
        markLoaded(runId);

        process(runId);

        assertEquals("SUCCEEDED", status(runId));
        assertEquals(1, count("dbo.patient"));
        assertEquals(List.of(
                "diagnoses.csv: icd_code is not a valid ICD-10 code",
                "labs.csv: patient_id not found in patients",
                "labs.csv: result_value is not a number",
                "patients.csv: date_of_birth is not a valid YYYY-MM-DD date",
                "patients.csv: gender must be M or F"), rejectReasons());
    }

    @Test
    void rerunningTheSameFilesChangesNothing() {
        process(loadValidFiles("555-0100", "8.1"));
        String updatedAt = db.queryForString("SELECT max(updated_at)::text FROM dbo.patient");

        long secondRun = loadValidFiles("555-0100", "8.1");
        process(secondRun);

        assertEquals("SUCCEEDED", status(secondRun));
        assertEquals(3, count("dbo.patient"));
        assertEquals(3, count("dbo.patient_diagnosis"));
        assertEquals(2, count("dbo.lab_result"));
        assertEquals(2, count("dbo.encounter"));
        assertEquals(updatedAt, db.queryForString("SELECT max(updated_at)::text FROM dbo.patient"));
    }

    @Test
    void upsertsOnTheNaturalKeys() {
        process(loadValidFiles("555-0100", "8.1"));

        process(loadValidFiles("555-9999", "6.4"));

        assertEquals(3, count("dbo.patient"), "patient_id is the key");
        assertEquals("555-9999", db.queryForString(
                "SELECT phone FROM dbo.patient WHERE source_patient_id = ?", patientIds.get(0)));
        assertEquals(2, count("dbo.lab_result"), "(patient_id, test_name, result_date) is the key");
        assertEquals("6.4000", db.queryForString("""
                SELECT r.result_value::text FROM dbo.lab_result r JOIN dbo.patient p USING (patient_id)
                WHERE p.source_patient_id = ?""", patientIds.get(0)));
    }

    @Test
    void refusesARunThatIsNotLoaded() {
        long runId = startRun();

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> process(runId));

        assertTrue(e.getCause().getMessage().contains("is STARTED, expected LOADED"), e.getCause().getMessage());
        assertEquals("STARTED", status(runId));
    }

    // --- The ETL's steps: start a run, truncate and load raw, mark it LOADED, call the procedure ---

    private long loadValidFiles(String firstPatientPhone, String firstHba1c) {
        long runId = startRun();
        rawPatient(patientIds.get(0), "1970-03-12", "F", firstPatientPhone);
        rawPatient(patientIds.get(1), "1985-07-01", "M", "555-0101");
        rawPatient(patientIds.get(2), "1955-05-05", "f", "555-0102");
        rawDiagnosis(patientIds.get(0), "E11.65", "Type 2 diabetes with hyperglycemia", "2020-01-01");
        rawDiagnosis(patientIds.get(0), "I10", "Essential hypertension", "2019-05-05");
        rawDiagnosis(patientIds.get(1), "E11.65", "Type 2 diabetes with hyperglycemia", "2021-03-03");
        rawLab(patientIds.get(0), "HbA1c", firstHba1c, "2024-01-10");
        rawLab(patientIds.get(1), "hba1c", "6.2", "2024-02-01");
        rawEncounter(patientIds.get(0), "PCP", "2024-01-10", pcpName);
        rawEncounter(patientIds.get(1), "Endocrinology", LocalDate.now().plusMonths(3).toString(), encounterProviderName);
        markLoaded(runId);
        return runId;
    }

    private long startRun() {
        long runId = db.queryForLong("INSERT INTO etl.load_run DEFAULT VALUES RETURNING run_id");
        db.execute("TRUNCATE raw.patients, raw.diagnoses, raw.labs, raw.encounters");
        return runId;
    }

    private void markLoaded(long runId) {
        db.update("UPDATE etl.load_run SET status = 'LOADED' WHERE run_id = ?", runId);
    }

    private void process(long runId) {
        db.execute("CALL etl.process_run(" + runId + ")");
    }

    private void rawPatient(String patientId, String dateOfBirth, String gender, String phone) {
        int i = patientIds.indexOf(patientId);
        db.update("INSERT INTO raw.patients VALUES (?, ?, ?, ?, ?, ?, ?, ?)", patientId,
                firstNames.get(i), lastNames.get(i), dateOfBirth, gender, phone, languageName, pcpName);
    }

    private void rawDiagnosis(String patientId, String icdCode, String description, String date) {
        db.update("INSERT INTO raw.diagnoses VALUES (?, ?, ?, ?)", patientId, icdCode, description, date);
    }

    private void rawLab(String patientId, String testName, String value, String date) {
        db.update("INSERT INTO raw.labs VALUES (?, ?, ?, ?)", patientId, testName, value, date);
    }

    private void rawEncounter(String patientId, String specialty, String date, String providerName) {
        db.update("INSERT INTO raw.encounters VALUES (?, ?, ?, ?)", patientId, specialty, date, providerName);
    }

    private String status(long runId) {
        return db.queryForString("SELECT status FROM etl.load_run WHERE run_id = ?", runId);
    }

    private long count(String table) {
        return db.queryForLong("SELECT count(*) FROM " + table);
    }

    private List<String> rejectReasons() {
        String joined = db.queryForString(
                "SELECT string_agg(file_name || ': ' || reason, '|' ORDER BY file_name, reason) FROM etl.load_reject");
        return joined == null ? List.of() : List.of(joined.split("\\|"));
    }
}
