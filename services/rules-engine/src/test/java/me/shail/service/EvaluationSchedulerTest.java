package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import me.shail.model.EvaluationRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationSchedulerTest {

    @Mock
    EvaluationService evaluationService;

    @Mock
    EvaluationStore store;

    @InjectMocks
    EvaluationScheduler scheduler;

    @Test
    void aNewerSuccessfulLoadThanTheLastEvaluationsIsNewData() {
        when(evaluationService.latestEtlRunId()).thenReturn(5L);
        when(store.latestSucceeded()).thenReturn(Optional.of(runOnLoad(4L)));

        assertTrue(scheduler.hasNewData());
    }

    @Test
    void theLoadTheLastEvaluationUsedIsNotNewData() {
        when(evaluationService.latestEtlRunId()).thenReturn(5L);
        when(store.latestSucceeded()).thenReturn(Optional.of(runOnLoad(5L)));

        assertFalse(scheduler.hasNewData());
    }

    @Test
    void aLoadAfterAnEvaluationWithoutDataIsNewData() {
        when(evaluationService.latestEtlRunId()).thenReturn(1L);
        when(store.latestSucceeded()).thenReturn(Optional.of(runOnLoad(null)));
        assertTrue(scheduler.hasNewData(), "evaluated before the first load");

        when(store.latestSucceeded()).thenReturn(Optional.empty());
        assertTrue(scheduler.hasNewData(), "never evaluated");
    }

    @Test
    void noSuccessfulLoadYetIsNoNewData() {
        when(evaluationService.latestEtlRunId()).thenReturn(null);

        assertFalse(scheduler.hasNewData());
        verify(store, never()).latestSucceeded();
    }

    @Test
    void newDataStartsADataChangedEvaluation() {
        when(evaluationService.latestEtlRunId()).thenReturn(2L);
        when(store.latestSucceeded()).thenReturn(Optional.of(runOnLoad(1L)));

        scheduler.checkForNewData();

        verify(evaluationService).run(EvaluationRun.Trigger.DATA_CHANGED);
    }

    @Test
    void theCheckSurvivesClinicalDataBeingDown() {
        when(evaluationService.latestEtlRunId()).thenThrow(new jakarta.ws.rs.ProcessingException("Connection refused"));

        scheduler.checkForNewData();

        verify(evaluationService, never()).run(EvaluationRun.Trigger.DATA_CHANGED);
    }

    @Test
    void startupRunsWhenTodayHasNoSuccessfulRun() {
        when(store.latestSucceeded()).thenReturn(Optional.of(run(LocalDate.now().minusDays(1))));

        scheduler.runIfNotDoneToday();

        verify(evaluationService).run(EvaluationRun.Trigger.STARTUP);
    }

    @Test
    void startupSkipsWhenTodayAlreadySucceeded() {
        when(store.latestSucceeded()).thenReturn(Optional.of(run(LocalDate.now())));

        scheduler.runIfNotDoneToday();

        verify(evaluationService, never()).run(EvaluationRun.Trigger.STARTUP);
    }

    @Test
    void theFirstStartupRuns() {
        when(store.latestSucceeded()).thenReturn(Optional.empty());

        scheduler.runIfNotDoneToday();

        verify(evaluationService).run(EvaluationRun.Trigger.STARTUP);
    }

    @Test
    void theDailyRunIsSkippedWhileAnotherIsRunning() {
        when(evaluationService.run(EvaluationRun.Trigger.SCHEDULED)).thenThrow(new EvaluationInProgressException());

        scheduler.daily();

        verify(evaluationService).run(EvaluationRun.Trigger.SCHEDULED);
    }

    private static EvaluationRun runOnLoad(Long etlRunId) {
        EvaluationRun run = run(LocalDate.now());
        run.sourceEtlRunId = etlRunId;
        return run;
    }

    private static EvaluationRun run(LocalDate asOf) {
        EvaluationRun run = new EvaluationRun();
        run.asOfDate = asOf;
        run.status = EvaluationRun.Status.SUCCEEDED;
        return run;
    }
}
