package me.shail.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.shail.model.CareNeed;
import me.shail.model.EvaluationRun;
import me.shail.model.PatientProgram;
import me.shail.model.ProgramVersion;
import me.shail.repository.CareNeedRepository;
import me.shail.repository.EvaluationRunRepository;
import me.shail.repository.PatientProgramRepository;
import me.shail.repository.ProgramVersionRepository;
import me.shail.rules.ProgramCatalog;
import me.shail.rules.ProgramDefinition;
import me.shail.rules.ProgramDefinitionException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * The transactional steps of an evaluation run. Each method commits on its own, so results are saved a
 * page at a time and a failed run still records why it failed.
 */
@ApplicationScoped
public class EvaluationStore {

    private static final Logger LOG = Logger.getLogger(EvaluationStore.class);

    @Inject
    EvaluationRunRepository evaluationRunRepository;

    @Inject
    ProgramVersionRepository programVersionRepository;

    @Inject
    PatientProgramRepository patientProgramRepository;

    @Inject
    CareNeedRepository careNeedRepository;

    @ConfigProperty(name = "rules-engine.evaluation.keep-runs")
    int keepRuns;

    /** A program in force for a run, with the stored version its results point to. */
    public record ActiveProgram(Long versionId, ProgramDefinition definition) {
    }

    /** @throws EvaluationInProgressException when a run is already RUNNING */
    @Transactional
    public EvaluationRun createRun(EvaluationRun.Trigger trigger, LocalDate asOf) {
        if (evaluationRunRepository.countRunning() > 0) {
            throw new EvaluationInProgressException();
        }
        EvaluationRun run = new EvaluationRun();
        run.trigger = trigger;
        run.asOfDate = asOf;
        run.status = EvaluationRun.Status.RUNNING;
        run.startedAt = OffsetDateTime.now();
        evaluationRunRepository.insert(run);
        return run;
    }

    @Transactional
    public void recordSourceEtlRun(Long runId, Long etlRunId) {
        EvaluationRun run = evaluationRunRepository.findById(runId).orElseThrow();
        run.sourceEtlRunId = etlRunId;
        evaluationRunRepository.update(run);
    }

    /**
     * Records each loaded program's version and returns the programs to evaluate. A file that fails to
     * load falls back to its last good version, if there is one; every failure is noted on the run.
     */
    @Transactional
    public List<ActiveProgram> registerPrograms(Long runId, ProgramCatalog.Load load) {
        List<ActiveProgram> active = new ArrayList<>();
        Set<String> programIds = new HashSet<>();
        for (ProgramCatalog.LoadedProgram program : load.programs()) {
            active.add(new ActiveProgram(versionOf(program).id, program.definition()));
            programIds.add(program.definition().id());
        }

        List<String> problems = new ArrayList<>();
        for (ProgramCatalog.LoadError error : load.errors()) {
            String problem = error.sourceFile() + ": " + String.join("; ", error.errors());
            Optional<ProgramVersion> lastGood = programVersionRepository.findLatestBySourceFile(error.sourceFile());
            if (lastGood.isPresent() && !programIds.contains(lastGood.get().programId)) {
                try {
                    ProgramDefinition definition = ProgramCatalog.parse(error.sourceFile(), lastGood.get().definition).definition();
                    active.add(new ActiveProgram(lastGood.get().id, definition));
                    programIds.add(definition.id());
                    problem += " (using the last good version, loaded " + lastGood.get().loadedAt + ")";
                } catch (ProgramDefinitionException e) {
                    problem += " (the last good version no longer parses either)";
                }
            } else {
                problem += " (skipped)";
            }
            LOG.warnf("Care program %s", problem);
            problems.add(problem);
        }

        if (!problems.isEmpty()) {
            EvaluationRun run = evaluationRunRepository.findById(runId).orElseThrow();
            run.programErrors = String.join("\n", problems);
            evaluationRunRepository.update(run);
        }
        return active;
    }

    private ProgramVersion versionOf(ProgramCatalog.LoadedProgram program) {
        return programVersionRepository.find(program.definition().id(), program.checksum()).orElseGet(() -> {
            ProgramVersion version = new ProgramVersion();
            version.programId = program.definition().id();
            version.name = program.definition().name();
            version.shortName = program.definition().shortName();
            version.sourceFile = program.sourceFile();
            version.checksum = program.checksum();
            version.definition = program.yaml();
            programVersionRepository.insert(version);
            LOG.infof("Care program %s: new version %d from %s", version.programId, version.id, version.sourceFile);
            return version;
        });
    }

    @Transactional
    public void saveResults(List<PatientProgram> patientPrograms, List<CareNeed> careNeeds) {
        patientProgramRepository.insertAll(patientPrograms);
        careNeedRepository.insertAll(careNeeds);
    }

    @Transactional
    public EvaluationRun complete(Long runId, int patientsEvaluated) {
        EvaluationRun run = evaluationRunRepository.findById(runId).orElseThrow();
        run.status = EvaluationRun.Status.SUCCEEDED;
        run.patientsEvaluated = patientsEvaluated;
        run.finishedAt = OffsetDateTime.now();
        evaluationRunRepository.update(run);
        int deleted = evaluationRunRepository.deleteAllButNewest(keepRuns);
        if (deleted > 0) {
            LOG.infof("Deleted %d old evaluation run(s), keeping the newest %d", deleted, keepRuns);
        }
        return run;
    }

    @Transactional
    public EvaluationRun fail(Long runId, String errorMessage) {
        EvaluationRun run = evaluationRunRepository.findById(runId).orElseThrow();
        run.status = EvaluationRun.Status.FAILED;
        run.errorMessage = errorMessage.length() > 4000 ? errorMessage.substring(0, 4000) : errorMessage;
        run.finishedAt = OffsetDateTime.now();
        evaluationRunRepository.update(run);
        return run;
    }

    @Transactional
    public int failAbandonedRuns() {
        return evaluationRunRepository.failAbandoned(OffsetDateTime.now());
    }

    @Transactional
    public Optional<EvaluationRun> latestSucceeded() {
        return evaluationRunRepository.findLatestSucceeded();
    }
}
