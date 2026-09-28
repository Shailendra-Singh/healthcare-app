package me.shail.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.data.page.PageRequest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import me.shail.support.ReactiveSessions;
import me.shail.support.TestData;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The bulk queries behind GET /evaluation-inputs, against real PostgreSQL. */
@QuarkusTest
class EvaluationInputQueriesTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 6, 1);

    @Inject
    PatientRepository patientRepository;

    @Inject
    PatientDiagnosisRepository patientDiagnosisRepository;

    @Inject
    LabResultRepository labResultRepository;

    @Inject
    EncounterRepository encounterRepository;

    @Inject
    ReactiveSessions sessions;

    @Inject
    TestDatabase db;

    long patientId;
    long otherPatientId;

    @BeforeEach
    void seed() {
        db.reset();
        db.insertDiagnosisCode("E11.65", TestData.FAKER.medical().diseaseName(), "E11");
        db.insertDiagnosisCode("Z00.00", TestData.FAKER.medical().diseaseName(), null);
        short hba1c = db.insertLabTest("HbA1c");
        short ldl = db.insertLabTest("LDL");
        short endocrinology = db.insertSpecialty("Endocrinology");
        short pcp = db.insertSpecialty("PCP");

        patientId = db.insertPatient();
        otherPatientId = db.insertPatient();
        db.insertPatient();

        db.insertPatientDiagnosis(patientId, "E11.65", LocalDate.of(2020, 1, 1));
        db.insertPatientDiagnosis(patientId, "Z00.00", LocalDate.of(2021, 1, 1));
        db.insertPatientDiagnosis(otherPatientId, "Z00.00", LocalDate.of(2022, 1, 1));

        db.insertLabResult(patientId, hba1c, new BigDecimal("8.2"), LocalDate.of(2025, 1, 10));
        db.insertLabResult(patientId, hba1c, new BigDecimal("9.4"), LocalDate.of(2026, 5, 2));
        db.insertLabResult(patientId, ldl, new BigDecimal("130"), LocalDate.of(2026, 3, 1));

        db.insertEncounter(patientId, endocrinology, null, LocalDate.of(2025, 2, 1));
        db.insertEncounter(patientId, endocrinology, null, AS_OF);                      // today counts as a visit
        db.insertEncounter(patientId, endocrinology, null, LocalDate.of(2026, 9, 1));
        db.insertEncounter(patientId, endocrinology, null, LocalDate.of(2026, 7, 1));  // soonest future
        db.insertEncounter(patientId, pcp, null, LocalDate.of(2024, 3, 3));
        db.insertEncounter(otherPatientId, pcp, null, LocalDate.of(2026, 8, 8));
    }

    @Test
    @RunOnVertxContext
    void findPageForEvaluationPagesById(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> patientRepository.findPageForEvaluation(PageRequest.ofPage(1, 2, false))),
                page -> assertEquals(List.of(patientId, otherPatientId), page.stream().map(p -> p.id).toList()));
        asserter.assertThat(() -> sessions.read(() -> patientRepository.findPageForEvaluation(PageRequest.ofPage(2, 2, false))),
                page -> assertEquals(1, page.size()));
    }

    @Test
    @RunOnVertxContext
    void diagnosisFactsIncludeTheConditionGroupWhenThereIsOne(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> patientDiagnosisRepository.findFactsByPatients(List.of(patientId))), rows -> {
            Map<String, Object[]> byCode = rows.stream().collect(Collectors.toMap(r -> (String) r[1], r -> r));
            assertEquals(2, rows.size());
            assertEquals(List.of(patientId, "E11.65", "E11", true, LocalDate.of(2020, 1, 1)), Arrays.asList(byCode.get("E11.65")));
            assertNull(byCode.get("Z00.00")[2]);
            assertNull(byCode.get("Z00.00")[3]);
        });
    }

    @Test
    @RunOnVertxContext
    void latestLabResultIsReturnedOncePerTest(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> labResultRepository.findLatestPerTestByPatients(List.of(patientId, otherPatientId))), rows -> {
            Map<String, Object[]> byTest = rows.stream().collect(Collectors.toMap(r -> (String) r[1], r -> r));
            assertEquals(2, rows.size());
            assertEquals(0, new BigDecimal("9.4").compareTo((BigDecimal) byTest.get("HbA1c")[2]));
            assertEquals(LocalDate.of(2026, 5, 2), byTest.get("HbA1c")[3]);
            assertEquals(LocalDate.of(2026, 3, 1), byTest.get("LDL")[3]);
        });
    }

    @Test
    @RunOnVertxContext
    void visitSummarySplitsPastAndFutureAtAsOf(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> encounterRepository.summarizeVisitsByPatients(List.of(patientId, otherPatientId), AS_OF)), rows -> {
            Map<String, Object[]> byKey = rows.stream().collect(Collectors.toMap(r -> r[0] + "/" + r[1], r -> r));
            assertEquals(3, rows.size());
            Object[] endo = byKey.get(patientId + "/Endocrinology");
            assertEquals(AS_OF, endo[2], "a visit on the as-of date is the last visit");
            assertEquals(LocalDate.of(2026, 7, 1), endo[3], "the soonest future appointment");
            Object[] pcp = byKey.get(patientId + "/PCP");
            assertEquals(LocalDate.of(2024, 3, 3), pcp[2]);
            assertNull(pcp[3]);
            Object[] otherPcp = byKey.get(otherPatientId + "/PCP");
            assertNull(otherPcp[2], "never seen");
            assertEquals(LocalDate.of(2026, 8, 8), otherPcp[3]);
        });
    }
}
