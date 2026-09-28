package me.shail.service;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.LocalDate;
import me.shail.model.EvaluationRun;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Runs the evaluation daily ({@code rules-engine.evaluation.cron}), after a restart once on startup if today has
 * no successful run yet, and whenever clinical-data finishes a new successful ETL load (checked every
 * {@code rules-engine.evaluation.etl-check-every}).
 */
@ApplicationScoped
public class EvaluationScheduler {

    private static final Logger LOG = Logger.getLogger(EvaluationScheduler.class);

    @Inject
    EvaluationService evaluationService;

    @Inject
    EvaluationStore store;

    @ConfigProperty(name = "rules-engine.evaluation.run-on-startup")
    boolean runOnStartup;

    @ConfigProperty(name = "rules-engine.evaluation.startup-delay")
    Duration startupDelay;

    @Scheduled(identity = "daily-evaluation", cron = "{rules-engine.evaluation.cron}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void daily() {
        runUnlessBusy(EvaluationRun.Trigger.SCHEDULED);
    }

    void onStartup(@Observes StartupEvent event) {
        int abandoned = store.failAbandonedRuns();
        if (abandoned > 0) {
            LOG.warnf("Marked %d run(s) left RUNNING by an earlier process as FAILED", abandoned);
        }
        if (runOnStartup) {
            // Delayed, so the app (and clinical-data, when started together) is up first
            Uni.createFrom().voidItem().onItem().delayIt().by(startupDelay)
                    .subscribe().with(ignored -> runIfNotDoneToday(), e -> LOG.error("Startup evaluation failed", e));
        }
    }

    @Scheduled(identity = "etl-check", every = "{rules-engine.evaluation.etl-check-every}",
            delayed = "{rules-engine.evaluation.startup-delay}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void checkForNewData() {
        try {
            if (hasNewData()) {
                LOG.info("clinical-data has a new successful ETL load; re-evaluating");
                runUnlessBusy(EvaluationRun.Trigger.DATA_CHANGED);
            }
        } catch (RuntimeException e) {
            // e.g. clinical-data is down: check again next time
            LOG.warnf("Could not check clinical-data for a new ETL load: %s", e.getMessage());
        }
    }

    /** True when clinical-data's latest successful ETL load is newer than the one the latest evaluation used. */
    boolean hasNewData() {
        Long latestLoad = evaluationService.latestEtlRunId();
        if (latestLoad == null) {
            return false;
        }
        Long evaluatedLoad = store.latestSucceeded().map(run -> run.sourceEtlRunId).orElse(null);
        return evaluatedLoad == null || latestLoad > evaluatedLoad;
    }

    void runIfNotDoneToday() {
        boolean doneToday = store.latestSucceeded()
                .map(run -> run.asOfDate.equals(LocalDate.now()))
                .orElse(false);
        if (doneToday) {
            LOG.info("Today's evaluation already succeeded; no startup run");
        } else {
            runUnlessBusy(EvaluationRun.Trigger.STARTUP);
        }
    }

    private void runUnlessBusy(EvaluationRun.Trigger trigger) {
        try {
            evaluationService.run(trigger);
        } catch (EvaluationInProgressException e) {
            LOG.infof("Skipping the %s evaluation: another one is still running", trigger);
        }
    }
}
