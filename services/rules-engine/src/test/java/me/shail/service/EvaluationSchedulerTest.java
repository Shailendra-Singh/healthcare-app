package me.shail.service;

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

    private static EvaluationRun run(LocalDate asOf) {
        EvaluationRun run = new EvaluationRun();
        run.asOfDate = asOf;
        run.status = EvaluationRun.Status.SUCCEEDED;
        return run;
    }
}
