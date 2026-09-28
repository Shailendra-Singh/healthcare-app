package me.shail.service;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import me.shail.model.GenerationRun;
import org.jboss.logging.Logger;

/**
 * Checks the rules-engine every {@code task-generation.poll-every} and reconciles when it has a newer successful
 * evaluation than the last one reconciled.
 */
@ApplicationScoped
public class TaskGenerationScheduler {

    private static final Logger LOG = Logger.getLogger(TaskGenerationScheduler.class);

    @Inject
    TaskGenerationService taskGenerationService;

    @Inject
    TaskStore store;

    void onStartup(@Observes StartupEvent event) {
        int abandoned = store.failAbandonedRuns();
        if (abandoned > 0) {
            LOG.warnf("Marked %d run(s) left RUNNING by an earlier process as FAILED", abandoned);
        }
    }

    @Scheduled(identity = "task-generation-poll", every = "{task-generation.poll-every}",
            delayed = "{task-generation.startup-delay}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void poll() {
        try {
            taskGenerationService.run(GenerationRun.Trigger.SCHEDULED);
        } catch (GenerationInProgressException e) {
            LOG.info("Skipping this check: a task generation run is still running");
        } catch (RuntimeException e) {
            // e.g. the rules-engine is down: try again at the next check
            LOG.warnf("Could not check the rules-engine for a new evaluation: %s", e.getMessage());
        }
    }
}
