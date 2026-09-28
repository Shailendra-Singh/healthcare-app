package me.shail.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import me.shail.client.ClinicalDataClient;
import me.shail.client.EtlRun;
import me.shail.client.EvaluationInput;
import me.shail.model.CareNeed;
import me.shail.model.EvaluationRun;
import me.shail.model.PatientProgram;
import me.shail.rules.PatientFacts;
import me.shail.rules.ProgramCatalog;
import me.shail.rules.ProgramEvaluator;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Runs an evaluation: loads the care programs, pages through every patient from clinical-data, applies
 * each program and saves the results a page at a time.
 */
@ApplicationScoped
public class EvaluationService {

    private static final Logger LOG = Logger.getLogger(EvaluationService.class);

    @Inject
    EvaluationStore store;

    @Inject
    ProgramCatalog catalog;

    @Inject
    @RestClient
    ClinicalDataClient clinicalData;

    @ConfigProperty(name = "rules-engine.evaluation.page-size")
    int pageSize;

    /** Replaced in tests to evaluate as of a fixed date. */
    Clock clock = Clock.systemDefaultZone();

    /** Creates the run; call {@link #execute} next, possibly on another thread. */
    public EvaluationRun start(EvaluationRun.Trigger trigger) {
        return store.createRun(trigger, LocalDate.now(clock));
    }

    /** Starts and executes a run on the calling thread. */
    public EvaluationRun run(EvaluationRun.Trigger trigger) {
        return execute(start(trigger));
    }

    /** Never throws: a failure is recorded on the run, which is returned either way. */
    public EvaluationRun execute(EvaluationRun run) {
        LOG.infof("Evaluation run %d (%s) as of %s started", run.id, run.trigger, run.asOfDate);
        try {
            store.recordSourceEtlRun(run.id, latestEtlRunId());
            List<EvaluationStore.ActiveProgram> programs = store.registerPrograms(run.id, catalog.load());

            int patients = 0;
            for (long page = 1; ; page++) {
                List<EvaluationInput> inputs = clinicalData.evaluationInputs(page, pageSize, run.asOfDate);
                if (inputs.isEmpty()) {
                    break;
                }
                saveResults(run, programs, inputs);
                patients += inputs.size();
                if (inputs.size() < pageSize) {
                    break;
                }
            }

            EvaluationRun completed = store.complete(run.id, patients);
            LOG.infof("Evaluation run %d SUCCEEDED: %d patients, %d programs", run.id, patients, programs.size());
            return completed;
        } catch (Exception e) {
            LOG.errorf(e, "Evaluation run %d FAILED", run.id);
            return store.fail(run.id, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void saveResults(EvaluationRun run, List<EvaluationStore.ActiveProgram> programs, List<EvaluationInput> inputs) {
        List<PatientProgram> patientPrograms = new ArrayList<>();
        List<CareNeed> careNeeds = new ArrayList<>();
        for (EvaluationInput input : inputs) {
            PatientFacts facts = input.toFacts();
            for (EvaluationStore.ActiveProgram program : programs) {
                ProgramEvaluator.evaluate(program.definition(), facts, run.asOfDate).ifPresent(result -> {
                    patientPrograms.add(patientProgram(run.id, program.versionId(), facts, result));
                    result.needs().forEach(need -> careNeeds.add(careNeed(run.id, facts, result, need)));
                });
            }
        }
        store.saveResults(patientPrograms, careNeeds);
    }

    private static PatientProgram patientProgram(Long runId, Long versionId, PatientFacts facts, ProgramEvaluator.Result result) {
        PatientProgram patientProgram = new PatientProgram();
        patientProgram.runId = runId;
        patientProgram.sourcePatientId = facts.sourcePatientId();
        patientProgram.programId = result.program().id();
        patientProgram.programVersionId = versionId;
        patientProgram.tierId = result.tier() == null ? null : result.tier().id();
        patientProgram.tierName = result.tier() == null ? null : result.tier().name();
        patientProgram.evidence = result.evidence();
        return patientProgram;
    }

    private static CareNeed careNeed(Long runId, PatientFacts facts, ProgramEvaluator.Result result,
            ProgramEvaluator.NeedResult need) {
        CareNeed careNeed = new CareNeed();
        careNeed.runId = runId;
        careNeed.sourcePatientId = facts.sourcePatientId();
        careNeed.programId = result.program().id();
        careNeed.specialty = need.need().visit();
        careNeed.tierId = result.tier().id();
        careNeed.everyDays = need.need().everyDays();
        careNeed.lastVisitDate = need.lastVisitDate();
        careNeed.dueDate = need.dueDate();
        careNeed.nextScheduledDate = need.nextScheduledDate();
        careNeed.status = need.status();
        careNeed.priority = need.need().priority();
        careNeed.note = need.need().note();
        return careNeed;
    }

    /**
     * clinical-data's latest successful ETL run; null before its first. Only successful loads count, so a run that
     * starts during a load never claims that load's data.
     */
    public Long latestEtlRunId() {
        try {
            EtlRun etlRun = clinicalData.latestEtlRun("SUCCEEDED");
            return etlRun == null ? null : etlRun.runId();
        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                return null;
            }
            throw e;
        }
    }
}
