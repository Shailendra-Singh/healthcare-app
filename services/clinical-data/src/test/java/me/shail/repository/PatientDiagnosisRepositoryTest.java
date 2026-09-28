package me.shail.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.time.LocalDate;
import java.util.List;
import me.shail.support.ReactiveSessions;
import me.shail.support.TestData;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PatientDiagnosisRepositoryTest {

    @Inject
    PatientDiagnosisRepository repository;

    @Inject
    ReactiveSessions sessions;

    @Inject
    TestDatabase db;

    long patientId;
    long olderId;
    long newerId;

    @BeforeEach
    void seed() {
        db.reset();
        db.insertDiagnosisCode("E11.65", TestData.FAKER.medical().diseaseName(), "E11");
        db.insertDiagnosisCode("I10", TestData.FAKER.medical().diseaseName(), "I10");
        patientId = db.insertPatient();
        long otherPatientId = db.insertPatient();
        olderId = db.insertPatientDiagnosis(patientId, "I10", LocalDate.of(2019, 5, 5));
        newerId = db.insertPatientDiagnosis(patientId, "E11.65", LocalDate.of(2021, 3, 3));
        db.insertPatientDiagnosis(otherPatientId, "E11.65", LocalDate.of(2022, 1, 1));
    }

    @Test
    @RunOnVertxContext
    void findWithDetailsFetchesPatientCodeAndGroup(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findWithDetails(newerId)), diagnosis -> {
            assertEquals(patientId, diagnosis.patient.id);
            assertEquals("E11.65", diagnosis.diagnosisCode.icdCode);
            assertEquals("E11", diagnosis.diagnosisCode.conditionGroup.icdPrefix);
            assertEquals(LocalDate.of(2021, 3, 3), diagnosis.diagnosedDate);
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
                diagnoses -> assertEquals(List.of(newerId, olderId), diagnoses.stream().map(d -> d.id).toList()));
    }
}
