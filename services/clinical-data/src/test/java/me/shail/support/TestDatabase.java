package me.shail.support;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/**
 * Seeds the test database over JDBC. The app's reactive pool is read-only, so tests write
 * through the JDBC datasource that Flyway uses.
 */
@ApplicationScoped
public class TestDatabase {

    @Inject
    AgroalDataSource dataSource;

    /** Empties everything except the condition groups seeded by V4. */
    public void reset() {
        execute("""
                TRUNCATE dbo.encounter, dbo.lab_result, dbo.patient_diagnosis, dbo.patient,
                         dbo.diagnosis_code, dbo.language, dbo.specialty, dbo.provider, dbo.lab_test,
                         raw.patients, raw.diagnoses, raw.labs, raw.encounters,
                         etl.load_reject, etl.load_file, etl.load_run, etl.heartbeat
                RESTART IDENTITY CASCADE""");
    }

    public short insertLanguage(String name) {
        return (short) insertReturningId("INSERT INTO dbo.language (language_name) VALUES (?) RETURNING language_id", name);
    }

    public short insertSpecialty(String name) {
        return (short) insertReturningId("INSERT INTO dbo.specialty (specialty_name) VALUES (?) RETURNING specialty_id", name);
    }

    public short insertLabTest(String name) {
        return (short) insertReturningId("INSERT INTO dbo.lab_test (test_name) VALUES (?) RETURNING lab_test_id", name);
    }

    public long insertProvider(String name) {
        return insertReturningId("INSERT INTO dbo.provider (provider_name) VALUES (?) RETURNING provider_id", name);
    }

    /** {@code groupPrefix} is one of the V4 condition group prefixes (e.g. E11), or null. */
    public void insertDiagnosisCode(String icdCode, String description, String groupPrefix) {
        update("""
                INSERT INTO dbo.diagnosis_code (icd_code, description, condition_group_id)
                VALUES (?, ?, (SELECT condition_group_id FROM dbo.condition_group WHERE icd_prefix = ?))""",
                icdCode, description, groupPrefix);
    }

    public long insertPatient(String sourcePatientId, String firstName, String lastName, LocalDate dateOfBirth,
            char gender, String phone, Short languageId, Long pcpProviderId) {
        return insertReturningId("""
                INSERT INTO dbo.patient (source_patient_id, first_name, last_name, date_of_birth, gender, phone,
                                         language_id, pcp_provider_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING patient_id""",
                sourcePatientId, firstName, lastName, Date.valueOf(dateOfBirth), String.valueOf(gender), phone,
                languageId, pcpProviderId);
    }

    /** A patient with fake demographics and no language or PCP. */
    public long insertPatient() {
        return insertPatient(TestData.sourcePatientId(), TestData.FAKER.name().firstName(),
                TestData.FAKER.name().lastName(), TestData.dateOfBirth(), TestData.gender(), TestData.phone(),
                null, null);
    }

    public long insertPatientDiagnosis(long patientId, String icdCode, LocalDate diagnosedDate) {
        return insertReturningId("""
                INSERT INTO dbo.patient_diagnosis (patient_id, icd_code, diagnosed_date)
                VALUES (?, ?, ?) RETURNING patient_diagnosis_id""",
                patientId, icdCode, Date.valueOf(diagnosedDate));
    }

    public long insertLabResult(long patientId, short labTestId, BigDecimal value, LocalDate resultDate) {
        return insertReturningId("""
                INSERT INTO dbo.lab_result (patient_id, lab_test_id, result_value, result_date)
                VALUES (?, ?, ?, ?) RETURNING lab_result_id""",
                patientId, labTestId, value, Date.valueOf(resultDate));
    }

    public long insertEncounter(long patientId, short specialtyId, Long providerId, LocalDate encounterDate) {
        return insertReturningId("""
                INSERT INTO dbo.encounter (patient_id, specialty_id, provider_id, encounter_date)
                VALUES (?, ?, ?, ?) RETURNING encounter_id""",
                patientId, specialtyId, providerId, Date.valueOf(encounterDate));
    }

    // --- Generic JDBC helpers ---

    public void execute(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    public int update(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = prepare(connection, sql, params)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    public long queryForLong(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = prepare(connection, sql, params);
                ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("No row: " + sql);
            }
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    public String queryForString(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = prepare(connection, sql, params);
                ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    private long insertReturningId(String sql, Object... params) {
        return queryForLong(sql, params);
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
        return statement;
    }
}
