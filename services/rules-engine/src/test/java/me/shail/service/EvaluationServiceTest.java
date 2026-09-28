package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.arc.ClientProxy;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import me.shail.client.ClinicalDataClient;
import me.shail.client.EtlRun;
import me.shail.client.EvaluationInput;
import me.shail.dto.CareNeedDto;
import me.shail.dto.EvaluationRunDto;
import me.shail.dto.PatientProgramDto;
import me.shail.model.EvaluationRun;
import me.shail.rules.ProgramCatalog;
import me.shail.rules.ProgramEvaluator.NeedStatus;
import me.shail.support.Inputs;
import me.shail.support.TestDatabase;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Full evaluation runs against a real rules_engine database, with clinical-data and the program folder
 * mocked. Results are read back through ResultsService, as the API does.
 */
@QuarkusTest
class EvaluationServiceTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 9, 28);

    @Inject
    EvaluationService evaluationService;

    @Inject
    EvaluationStore store;

    @Inject
    ResultsService results;

    @Inject
    TestDatabase db;

    @InjectMock
    @RestClient
    ClinicalDataClient clinicalData;

    @InjectMock
    ProgramCatalog catalog;

    EvaluationInput seniorDiabetic;
    EvaluationInput adult;
    EvaluationInput minor;

    @BeforeEach
    void setUp() {
        db.reset();
        EvaluationService service = ClientProxy.unwrap(evaluationService);
        service.clock = Clock.fixed(AS_OF.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service.pageSize = 2;
        ClientProxy.unwrap(store).keepRuns = 30;

        seniorDiabetic = Inputs.aged(70, AS_OF)
                .diagnosis("E11.65", "E11", true)
                .lab("HbA1c", "9.4", AS_OF.minusMonths(1))
                .visits("Endocrinology", AS_OF.minusDays(30), null)
                .visits("PCP", AS_OF.minusDays(400), AS_OF.plusDays(10))
                .build();
        adult = Inputs.aged(40, AS_OF).visits("PCP", AS_OF.minusDays(100), null).build();
        minor = Inputs.aged(12, AS_OF).build();

        when(catalog.load()).thenReturn(Inputs.repoPrograms());
        when(clinicalData.latestEtlRun("SUCCEEDED")).thenReturn(new EtlRun(7L, "SUCCEEDED"));
        when(clinicalData.evaluationInputs(1, 2, AS_OF)).thenReturn(List.of(seniorDiabetic, adult));
        when(clinicalData.evaluationInputs(2, 2, AS_OF)).thenReturn(List.of(minor));
    }

    @Test
    void evaluatesEveryPageOfPatientsAgainstEveryProgram() {
        EvaluationRun run = evaluationService.run(EvaluationRun.Trigger.MANUAL);

        assertEquals(EvaluationRun.Status.SUCCEEDED, run.status, run.errorMessage);
        assertEquals(3, run.patientsEvaluated);
        assertEquals(AS_OF, run.asOfDate);
        verify(clinicalData).evaluationInputs(1, 2, AS_OF);
        verify(clinicalData).evaluationInputs(2, 2, AS_OF);

        EvaluationRunDto dto = results.latestRun().orElseThrow();
        assertEquals(7L, dto.sourceEtlRunId());
        assertEquals(List.of(
                new EvaluationRunDto.TierCount("diabetes-management", "high-risk", 1),
                new EvaluationRunDto.TierCount("primary-care-wellness", "high-priority", 1),
                new EvaluationRunDto.TierCount("primary-care-wellness", "standard", 1)), dto.summary().tiers());
        assertEquals(Map.of("MET", 2L, "SCHEDULED", 1L, "OVERDUE", 4L), dto.summary().needsByStatus());
    }

    @Test
    void patientResultsExplainTheTierAndListTheNeeds() {
        evaluationService.run(EvaluationRun.Trigger.MANUAL);

        Map<String, PatientProgramDto> programs = results.patientPrograms(seniorDiabetic.sourcePatientId()).stream()
                .collect(Collectors.toMap(PatientProgramDto::programId, p -> p));

        PatientProgramDto diabetes = programs.get("diabetes-management");
        assertEquals("Diabetes Management", diabetes.programName());
        assertNull(diabetes.shortName());
        assertEquals("high-risk", diabetes.tierId());
        assertEquals(Map.of("value", 9.4, "date", AS_OF.minusMonths(1).toString()), diabetes.evidence().get("HbA1c"));
        assertEquals(List.of("E11.65"), diabetes.evidence().get("diagnoses"));
        Map<String, NeedStatus> needs = diabetes.needs().stream()
                .collect(Collectors.toMap(CareNeedDto::specialty, CareNeedDto::status));
        assertEquals(Map.of("Endocrinology", NeedStatus.MET, "Cardiology", NeedStatus.OVERDUE,
                "Podiatry", NeedStatus.OVERDUE, "Ophthalmology", NeedStatus.OVERDUE,
                "Nephrology", NeedStatus.OVERDUE), needs);

        PatientProgramDto wellness = programs.get("primary-care-wellness");
        assertEquals("PCP", wellness.shortName());
        assertEquals("high-priority", wellness.tierId());
        assertEquals(NeedStatus.SCHEDULED, wellness.needs().getFirst().status());
        assertEquals(AS_OF.plusDays(10), wellness.needs().getFirst().nextScheduledDate());

        assertTrue(results.patientPrograms(minor.sourcePatientId()).isEmpty(), "a 12-year-old without diabetes is in no program");
    }

    @Test
    void careNeedsCanBeFilteredAndAreSortedByDueDate() {
        evaluationService.run(EvaluationRun.Trigger.MANUAL);

        List<CareNeedDto> overdue = results.careNeeds(null, null, NeedStatus.OVERDUE, null, 1, 100);
        assertEquals(4, overdue.size());
        assertTrue(overdue.stream().allMatch(n -> n.status() == NeedStatus.OVERDUE));

        List<CareNeedDto> pcp = results.careNeeds("primary-care-wellness", null, null, "pcp", 1, 100);
        assertEquals(List.of(seniorDiabetic.sourcePatientId(), adult.sourcePatientId()),
                pcp.stream().map(CareNeedDto::sourcePatientId).toList(), "earliest due date first");

        assertEquals(1, results.careNeeds(null, null, null, null, 2, 3).size() - 2, "page 2 of size 3");
    }

    @Test
    void careNeedsCarryTheirProgramsTaskPolicy() {
        evaluationService.run(EvaluationRun.Trigger.MANUAL);

        List<CareNeedDto> needs = results.careNeeds(null, null, null, null, 1, 100);
        assertTrue(needs.stream().filter(n -> n.programId().equals("diabetes-management"))
                .allMatch(n -> n.tasks().equals(new CareNeedDto.TaskPolicy("scheduling", "referral"))));
        assertTrue(needs.stream().filter(n -> n.programId().equals("primary-care-wellness"))
                .allMatch(n -> n.tasks().equals(new CareNeedDto.TaskPolicy("scheduling", "none"))));
        assertEquals("referral", results.patientPrograms(seniorDiabetic.sourcePatientId()).stream()
                .filter(p -> p.programId().equals("diabetes-management")).findFirst().orElseThrow()
                .needs().getFirst().tasks().neverSeen());
    }

    @Test
    void careNeedsOfARunPageInAStableOrderAndStayOnThatRun() {
        EvaluationRun first = evaluationService.run(EvaluationRun.Trigger.MANUAL);
        when(clinicalData.evaluationInputs(1, 2, AS_OF)).thenReturn(List.of(adult));
        when(clinicalData.evaluationInputs(2, 2, AS_OF)).thenReturn(List.of());
        EvaluationRun second = evaluationService.run(EvaluationRun.Trigger.MANUAL);

        List<CareNeedDto> page1 = results.careNeedsOfRun(first.id, 1, 4).orElseThrow();
        List<CareNeedDto> page2 = results.careNeedsOfRun(first.id, 2, 4).orElseThrow();
        assertEquals(4, page1.size());
        assertEquals(3, page2.size(), "the first run's 7 needs, although a newer run has only 1");
        List<String> keys = java.util.stream.Stream.concat(page1.stream(), page2.stream())
                .map(n -> n.sourcePatientId() + "/" + n.programId() + "/" + n.specialty()).toList();
        assertEquals(keys.stream().sorted().toList(), keys, "ordered by patient, program, specialty");

        assertEquals(1, results.careNeedsOfRun(second.id, 1, 100).orElseThrow().size());
        assertEquals(second.id, results.latestSucceededRun().orElseThrow().runId());
    }

    @Test
    void careNeedsOfARunThatDidNotSucceedAreNotFound() {
        when(clinicalData.evaluationInputs(anyLong(), anyInt(), eq(AS_OF))).thenThrow(new ProcessingException("down"));
        EvaluationRun failed = evaluationService.run(EvaluationRun.Trigger.MANUAL);

        assertTrue(results.careNeedsOfRun(failed.id, 1, 10).isEmpty());
        assertTrue(results.careNeedsOfRun(999L, 1, 10).isEmpty());
        assertTrue(results.latestSucceededRun().isEmpty());
    }

    @Test
    void aFailureIsRecordedOnTheRunAndKeepsThePreviousResults() {
        evaluationService.run(EvaluationRun.Trigger.MANUAL);
        when(clinicalData.evaluationInputs(anyLong(), anyInt(), eq(AS_OF))).thenThrow(new ProcessingException("Connection refused"));

        EvaluationRun failed = evaluationService.run(EvaluationRun.Trigger.SCHEDULED);

        assertEquals(EvaluationRun.Status.FAILED, failed.status);
        assertEquals("ProcessingException: Connection refused", failed.errorMessage);
        assertEquals(EvaluationRun.Status.FAILED, results.latestRun().orElseThrow().status());
        assertEquals(2, results.patientPrograms(seniorDiabetic.sourcePatientId()).size(),
                "results still come from the last successful run");
    }

    @Test
    void onlyOneRunAtATime() {
        store.createRun(EvaluationRun.Trigger.SCHEDULED, AS_OF);

        assertThrows(EvaluationInProgressException.class, () -> evaluationService.start(EvaluationRun.Trigger.MANUAL));
        assertEquals(1, store.failAbandonedRuns(), "a restart releases a run left RUNNING");
        assertEquals(EvaluationRun.Status.SUCCEEDED, evaluationService.run(EvaluationRun.Trigger.MANUAL).status);
    }

    @Test
    void anInvalidProgramFileFallsBackToItsLastGoodVersion() {
        evaluationService.run(EvaluationRun.Trigger.MANUAL);
        when(catalog.load()).thenReturn(new ProgramCatalog.Load(
                List.of(Inputs.repoProgram("primary-care-wellness.yaml")),
                List.of(new ProgramCatalog.LoadError("diabetes-management.yaml", List.of("tiers[0].criteria.lab: unknown key 'withinMonth'")))));

        EvaluationRun run = evaluationService.run(EvaluationRun.Trigger.MANUAL);

        EvaluationRunDto dto = results.run(run.id).orElseThrow();
        assertEquals(1, dto.programErrors().size());
        assertTrue(dto.programErrors().getFirst().startsWith(
                "diabetes-management.yaml: tiers[0].criteria.lab: unknown key 'withinMonth' (using the last good version"),
                dto.programErrors().getFirst());
        assertTrue(dto.summary().tiers().stream().anyMatch(t -> t.programId().equals("diabetes-management")),
                "diabetes results still produced from the last good version");
    }

    @Test
    void anInvalidNewProgramFileIsSkipped() {
        when(catalog.load()).thenReturn(new ProgramCatalog.Load(
                List.of(Inputs.repoProgram("primary-care-wellness.yaml")),
                List.of(new ProgramCatalog.LoadError("new-program.yaml", List.of("name: required")))));

        EvaluationRun run = evaluationService.run(EvaluationRun.Trigger.MANUAL);

        assertEquals(List.of("new-program.yaml: name: required (skipped)"), results.run(run.id).orElseThrow().programErrors());
    }

    @Test
    void aDataChangedRunIsRecordedWithItsTrigger() {
        EvaluationRun run = evaluationService.run(EvaluationRun.Trigger.DATA_CHANGED);

        assertEquals(EvaluationRun.Status.SUCCEEDED, run.status, run.errorMessage);
        assertEquals(EvaluationRun.Trigger.DATA_CHANGED, results.latestRun().orElseThrow().trigger());
        assertEquals(7L, results.latestRun().orElseThrow().sourceEtlRunId());
    }

    @Test
    void theSourceEtlRunIsEmptyBeforeClinicalDataHasLoadedAnything() {
        when(clinicalData.latestEtlRun("SUCCEEDED")).thenThrow(new WebApplicationException(404));

        EvaluationRun run = evaluationService.run(EvaluationRun.Trigger.MANUAL);

        assertEquals(EvaluationRun.Status.SUCCEEDED, run.status);
        assertNull(results.run(run.id).orElseThrow().sourceEtlRunId());
    }

    @Test
    void oldRunsAreDeletedAfterASuccessfulRun() {
        ClientProxy.unwrap(store).keepRuns = 2;

        evaluationService.run(EvaluationRun.Trigger.MANUAL);
        evaluationService.run(EvaluationRun.Trigger.MANUAL);
        EvaluationRun third = evaluationService.run(EvaluationRun.Trigger.MANUAL);

        assertEquals(2, db.queryForLong("SELECT count(*) FROM eval.evaluation_run"));
        assertEquals(third.id - 1, db.queryForLong("SELECT min(run_id) FROM eval.evaluation_run"));
        assertEquals(0, db.queryForLong("SELECT count(*) FROM eval.care_need WHERE run_id < ?", third.id - 1),
                "results of deleted runs go with them");
    }
}
