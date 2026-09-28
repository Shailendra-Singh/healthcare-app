package me.shail.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import me.shail.support.ReactiveSessions;
import me.shail.support.TestData;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LabResultRepositoryTest {

    @Inject
    LabResultRepository repository;

    @Inject
    ReactiveSessions sessions;

    @Inject
    TestDatabase db;

    long patientId;
    long oldHba1cId;
    long newHba1cId;
    long ldlId;
    BigDecimal newHba1cValue;

    @BeforeEach
    void seed() {
        db.reset();
        short hba1c = db.insertLabTest("HbA1c");
        short ldl = db.insertLabTest("LDL");
        patientId = db.insertPatient();
        long otherPatientId = db.insertPatient();
        newHba1cValue = TestData.resultValue();
        oldHba1cId = db.insertLabResult(patientId, hba1c, TestData.resultValue(), LocalDate.of(2023, 1, 10));
        newHba1cId = db.insertLabResult(patientId, hba1c, newHba1cValue, LocalDate.of(2024, 6, 1));
        ldlId = db.insertLabResult(patientId, ldl, TestData.resultValue(), LocalDate.of(2024, 2, 1));
        db.insertLabResult(otherPatientId, hba1c, TestData.resultValue(), LocalDate.of(2025, 1, 1));
    }

    @Test
    @RunOnVertxContext
    void findWithDetailsFetchesPatientAndTest(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findWithDetails(newHba1cId)), result -> {
            assertEquals(patientId, result.patient.id);
            assertEquals("HbA1c", result.labTest.name);
            assertEquals(0, newHba1cValue.compareTo(result.resultValue));
        });
    }

    @Test
    @RunOnVertxContext
    void findWithDetailsFailsWithNoResultWhenMissing(UniAsserter asserter) {
        asserter.assertFailedWith(() -> sessions.read(() -> repository.findWithDetails(-1L)),
                e -> assertInstanceOf(NoResultException.class, e));
    }

    @Test
    @RunOnVertxContext
    void findByPatientReturnsOnlyThatPatientNewestFirst(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findByPatient(patientId)),
                results -> assertEquals(List.of(newHba1cId, ldlId, oldHba1cId), results.stream().map(r -> r.id).toList()));
    }

    @Test
    @RunOnVertxContext
    void findByPatientAndTestIgnoresCaseAndSortsNewestFirst(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findByPatientAndTest(patientId, "hba1C")),
                results -> assertEquals(List.of(newHba1cId, oldHba1cId), results.stream().map(r -> r.id).toList()));
    }

    @Test
    @RunOnVertxContext
    void findByPatientAndTestIsEmptyForATestThePatientNeverHad(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findByPatientAndTest(patientId, "TSH")),
                results -> assertTrue(results.isEmpty()));
    }
}
