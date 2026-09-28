package me.shail.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import me.shail.support.Patients;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the real files in care-programs/ against the scenarios in the program specification, so an edit to
 * a rule file that changes its meaning fails here.
 */
class CareProgramFilesTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 9, 28);

    static ProgramDefinition wellness;
    static ProgramDefinition diabetes;

    @BeforeAll
    static void loadRepoPrograms() {
        ProgramCatalog catalog = new ProgramCatalog();
        catalog.programsDir = Path.of("../../care-programs");
        ProgramCatalog.Load load = catalog.load();
        assertTrue(load.errors().isEmpty(), "care-programs/ has invalid files: " + load.errors());
        Map<String, ProgramDefinition> byId = load.programs().stream()
                .collect(Collectors.toMap(p -> p.definition().id(), ProgramCatalog.LoadedProgram::definition));
        wellness = byId.get("primary-care-wellness");
        diabetes = byId.get("diabetes-management");
        assertEquals("PCP", wellness.shortName());
    }

    @Test
    void taskPoliciesFollowTheTaskGenerationCriteria() {
        assertEquals(new ProgramDefinition.TaskPolicy("scheduling", "none"), wellness.tasks(),
                "PCP: scheduling when past cadence; no task without PCP history");
        assertEquals(new ProgramDefinition.TaskPolicy("scheduling", "referral"), diabetes.tasks(),
                "specialists: scheduling when past cadence; referral without a prior encounter");
    }

    // --- Primary Care Wellness ---

    @Test
    void wellnessExcludesMinors() {
        assertTrue(ProgramEvaluator.evaluate(wellness, Patients.aged(17, AS_OF).build(), AS_OF).isEmpty());
    }

    @Test
    void wellnessHighPriorityFromAge65() {
        assertEquals("high-priority", tier(wellness, Patients.aged(65, AS_OF)));
        assertEquals("standard", tier(wellness, Patients.aged(64, AS_OF)));
    }

    @Test
    void wellnessHighPriorityForAnyChronicDiagnosis() {
        assertEquals("high-priority", tier(wellness, Patients.aged(30, AS_OF).diagnosis("J45.20", "J45", true)));
        assertEquals("standard", tier(wellness, Patients.aged(30, AS_OF).diagnosis("Z00.00", null, false)));
    }

    @Test
    void wellnessNeedsAPcpVisitEvery180Or365Days() {
        var high = ProgramEvaluator.evaluate(wellness,
                Patients.aged(70, AS_OF).visits("PCP", AS_OF.minusDays(200), null).build(), AS_OF).orElseThrow();
        assertEquals(List.of("PCP:180:OVERDUE"), needs(high));

        var standard = ProgramEvaluator.evaluate(wellness,
                Patients.aged(40, AS_OF).visits("PCP", AS_OF.minusDays(200), null).build(), AS_OF).orElseThrow();
        assertEquals(List.of("PCP:365:MET"), needs(standard));
    }

    // --- Diabetes Management ---

    @Test
    void diabetesIsForType1OrType2Only() {
        assertTrue(ProgramEvaluator.evaluate(diabetes, Patients.aged(50, AS_OF).diagnosis("E11.9", "E11", true).build(), AS_OF).isPresent());
        assertTrue(ProgramEvaluator.evaluate(diabetes, Patients.aged(12, AS_OF).diagnosis("E10.9", "E10", true).build(), AS_OF).isPresent(),
                "no age limit");
        assertTrue(ProgramEvaluator.evaluate(diabetes, Patients.aged(50, AS_OF).diagnosis("I10", "I10", true).build(), AS_OF).isEmpty());
    }

    @Test
    void diabetesTiersFollowTheLatestA1cInTheLast6Months() {
        assertEquals("high-risk", tier(diabetes, diabetic().lab("HbA1c", "9.0", AS_OF.minusMonths(1))));
        assertEquals("moderate-risk", tier(diabetes, diabetic().lab("HbA1c", "8.99", AS_OF.minusMonths(1))));
        assertEquals("moderate-risk", tier(diabetes, diabetic().lab("HbA1c", "7.0", AS_OF.minusMonths(1))));
        assertEquals("low-risk", tier(diabetes, diabetic().lab("HbA1c", "6.9", AS_OF.minusMonths(1))));
        assertEquals("unmonitored", tier(diabetes, diabetic().lab("HbA1c", "12.0", AS_OF.minusMonths(6).minusDays(1))));
        assertEquals("unmonitored", tier(diabetes, diabetic()));
    }

    @Test
    void diabetesHighRiskNeedsFiveSpecialties() {
        var result = ProgramEvaluator.evaluate(diabetes, diabetic().lab("HbA1c", "10.1", AS_OF.minusDays(10))
                .visits("Endocrinology", AS_OF.minusDays(30), null)
                .visits("Cardiology", AS_OF.minusDays(120), AS_OF.plusDays(5))
                .build(), AS_OF).orElseThrow();

        assertEquals(List.of("Endocrinology:90:MET", "Cardiology:90:SCHEDULED", "Podiatry:180:OVERDUE",
                "Ophthalmology:365:OVERDUE", "Nephrology:180:OVERDUE"), needs(result));
    }

    @Test
    void diabetesUnmonitoredNeedsEndocrinologyWithHighPriority() {
        var result = ProgramEvaluator.evaluate(diabetes, diabetic().build(), AS_OF).orElseThrow();

        assertEquals(List.of("Endocrinology:90:OVERDUE"), needs(result));
        assertEquals("high", result.needs().getFirst().need().priority());
        assertEquals("Get labs done", result.needs().getFirst().need().note());
        assertEquals("no result in the last 6 months", result.evidence().get("HbA1c"));
    }

    @Test
    void aPatientCanBeInBothPrograms() {
        var patient = diabetic().lab("HbA1c", "7.5", AS_OF.minusMonths(2)).build();

        assertTrue(ProgramEvaluator.evaluate(wellness, patient, AS_OF).isPresent());
        assertTrue(ProgramEvaluator.evaluate(diabetes, patient, AS_OF).isPresent());
    }

    private static Patients diabetic() {
        return Patients.aged(55, AS_OF).diagnosis("E11.65", "E11", true);
    }

    private static String tier(ProgramDefinition program, Patients patient) {
        Optional<ProgramEvaluator.Result> result = ProgramEvaluator.evaluate(program, patient.build(), AS_OF);
        return result.map(r -> r.tier().id()).orElse("not eligible");
    }

    private static List<String> needs(ProgramEvaluator.Result result) {
        return result.needs().stream()
                .map(n -> n.need().visit() + ":" + n.need().everyDays() + ":" + n.status())
                .toList();
    }
}
