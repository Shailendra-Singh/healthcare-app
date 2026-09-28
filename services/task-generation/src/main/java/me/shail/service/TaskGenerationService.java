package me.shail.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Optional;
import me.shail.client.CareNeed;
import me.shail.client.EvaluationRun;
import me.shail.client.RulesEngineClient;
import me.shail.model.GenerationRun;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Reconciles the tasks against the rules-engine's latest successful evaluation: pages through all of its care
 * needs, then closes the tasks it no longer calls for.
 */
@ApplicationScoped
public class TaskGenerationService {

    private static final Logger LOG = Logger.getLogger(TaskGenerationService.class);

    @Inject
    TaskStore store;

    @Inject
    @RestClient
    RulesEngineClient rulesEngine;

    @ConfigProperty(name = "task-generation.page-size")
    int pageSize;

    /**
     * Creates a run against the latest successful evaluation. Empty when there is nothing to do: the rules-engine
     * has no successful evaluation yet, or (for scheduled runs) the latest one was already reconciled. A manual
     * run always reconciles again, which changes nothing when nothing changed.
     *
     * @throws GenerationInProgressException when a run is already running
     */
    public Optional<GenerationRun> start(GenerationRun.Trigger trigger) {
        Optional<EvaluationRun> evaluation = latestEvaluation();
        if (evaluation.isEmpty()) {
            LOG.info("No successful rules-engine evaluation yet; nothing to generate");
            return Optional.empty();
        }
        Long evaluationRunId = evaluation.get().runId();
        if (trigger == GenerationRun.Trigger.SCHEDULED
                && store.latestReconciledEvaluationRun().filter(id -> id >= evaluationRunId).isPresent()) {
            LOG.debugf("Evaluation run %d is already reconciled", evaluationRunId);
            return Optional.empty();
        }
        return Optional.of(store.createRun(trigger, evaluationRunId));
    }

    /** Starts and executes a run on the calling thread. */
    public Optional<GenerationRun> run(GenerationRun.Trigger trigger) {
        return start(trigger).map(this::execute);
    }

    /** Never throws: a failure is recorded on the run, which is returned either way. */
    public GenerationRun execute(GenerationRun run) {
        LOG.infof("Task generation run %d (%s) for evaluation run %d started", run.id, run.trigger, run.evaluationRunId);
        try {
            TaskStore.Counts counts = new TaskStore.Counts(0, 0, 0);
            int needsRead = 0;
            for (int page = 1; ; page++) {
                List<CareNeed> needs = rulesEngine.careNeeds(run.evaluationRunId, page, pageSize);
                if (needs.isEmpty()) {
                    break;
                }
                counts = counts.plus(store.reconcilePage(run.evaluationRunId, needs));
                needsRead += needs.size();
                if (needs.size() < pageSize) {
                    break;
                }
            }
            counts = counts.plus(new TaskStore.Counts(0, 0, store.closeTasksNoLongerNeeded(run.evaluationRunId)));

            GenerationRun completed = store.complete(run.id, needsRead, counts);
            LOG.infof("Task generation run %d SUCCEEDED: %d needs read, %d tasks created, %d updated, %d closed",
                    run.id, needsRead, counts.created(), counts.updated(), counts.closed());
            return completed;
        } catch (Exception e) {
            LOG.errorf(e, "Task generation run %d FAILED", run.id);
            return store.fail(run.id, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Optional<EvaluationRun> latestEvaluation() {
        try {
            return Optional.ofNullable(rulesEngine.latestSucceeded("SUCCEEDED"));
        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
