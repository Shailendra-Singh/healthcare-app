package me.shail.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.data.page.PageRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import me.shail.dto.CareNeedDto;
import me.shail.dto.EvaluationRunDto;
import me.shail.dto.PatientProgramDto;
import me.shail.model.CareNeed;
import me.shail.model.EvaluationRun;
import me.shail.model.ProgramVersion;
import me.shail.repository.CareNeedRepository;
import me.shail.repository.EvaluationRunRepository;
import me.shail.repository.PatientProgramRepository;
import me.shail.repository.ProgramVersionRepository;
import me.shail.rules.ProgramEvaluator.NeedStatus;

/**
 * Reads evaluation results. Patient and care need results always come from the latest SUCCEEDED run, so
 * a run in progress or a failed run never shows partial data.
 */
@ApplicationScoped
@Transactional
public class ResultsService {

    @Inject
    EvaluationRunRepository evaluationRunRepository;

    @Inject
    PatientProgramRepository patientProgramRepository;

    @Inject
    CareNeedRepository careNeedRepository;

    @Inject
    ProgramVersionRepository programVersionRepository;

    public Optional<EvaluationRunDto> latestRun() {
        return evaluationRunRepository.findLatest().map(this::toDto);
    }

    public Optional<EvaluationRunDto> latestSucceededRun() {
        return evaluationRunRepository.findLatestSucceeded().map(this::toDto);
    }

    /** Runs newest first, each SUCCEEDED one with its summary; {@code page} starts at 1. */
    public List<EvaluationRunDto> runs(int page, int size) {
        return evaluationRunRepository.findNewestFirst(PageRequest.ofPage(page, size, false)).stream()
                .map(this::toDto)
                .toList();
    }

    public Optional<EvaluationRunDto> run(Long runId) {
        return evaluationRunRepository.findById(runId).map(this::toDto);
    }

    /** The programs a patient is in, by the latest successful run; empty when in none or before any run. */
    public List<PatientProgramDto> patientPrograms(String sourcePatientId) {
        Optional<EvaluationRun> run = evaluationRunRepository.findLatestSucceeded();
        if (run.isEmpty()) {
            return List.of();
        }
        var memberships = patientProgramRepository.findByPatient(run.get().id, sourcePatientId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<Long, ProgramVersion> versions = programVersionRepository
                .findByIds(memberships.stream().map(m -> m.programVersionId).distinct().toList())
                .stream().collect(Collectors.toMap(v -> v.id, Function.identity()));
        Map<String, CareNeedDto.TaskPolicy> policies = new java.util.HashMap<>();
        memberships.forEach(m -> policies.put(m.programId, CareNeedDto.TaskPolicy.of(versions.get(m.programVersionId))));
        Map<String, List<CareNeedDto>> needsByProgram = careNeedRepository.findByPatient(run.get().id, sourcePatientId)
                .stream().collect(Collectors.groupingBy(n -> n.programId, LinkedHashMap::new,
                        Collectors.mapping(n -> CareNeedDto.from(n, policies.get(n.programId)), Collectors.toList())));
        return memberships.stream()
                .map(m -> PatientProgramDto.from(m, versions.get(m.programVersionId),
                        needsByProgram.getOrDefault(m.programId, List.of())))
                .toList();
    }

    /** Care needs from the latest successful run, earliest due first; every filter is optional. */
    public List<CareNeedDto> careNeeds(String programId, String tierId, NeedStatus status, String specialty,
            int page, int size) {
        return evaluationRunRepository.findLatestSucceeded()
                .map(run -> withPolicies(run.id, careNeedRepository.search(run.id, programId, tierId, status, specialty, page, size)))
                .orElse(List.of());
    }

    /**
     * One page of every care need of a successful run, in a stable order (patient, program, specialty), so a
     * consumer can page through a whole run even while newer runs finish. Empty when the run is not a
     * SUCCEEDED run (or was deleted as an old run).
     */
    public Optional<List<CareNeedDto>> careNeedsOfRun(Long runId, int page, int size) {
        return evaluationRunRepository.findByIdAndStatus(runId, EvaluationRun.Status.SUCCEEDED)
                .map(run -> withPolicies(run.id, careNeedRepository.findPageByRun(run.id, PageRequest.ofPage(page, size, false))));
    }

    /** Adds each program's task policy, from the program versions the run used. */
    private List<CareNeedDto> withPolicies(Long runId, List<CareNeed> needs) {
        Map<String, CareNeedDto.TaskPolicy> policies = new java.util.HashMap<>();
        for (ProgramVersion version : programVersionRepository.findByIds(patientProgramRepository.findProgramVersionIds(runId))) {
            CareNeedDto.TaskPolicy policy = CareNeedDto.TaskPolicy.of(version);
            if (policy != null) {
                policies.put(version.programId, policy);
            }
        }
        return needs.stream().map(n -> CareNeedDto.from(n, policies.get(n.programId))).toList();
    }

    private EvaluationRunDto toDto(EvaluationRun run) {
        if (run.status != EvaluationRun.Status.SUCCEEDED) {
            return EvaluationRunDto.from(run, null);
        }
        List<EvaluationRunDto.TierCount> tiers = patientProgramRepository.countByTier(run.id).stream()
                .map(row -> new EvaluationRunDto.TierCount((String) row[0], (String) row[1], (Long) row[2]))
                .toList();
        Map<String, Long> needsByStatus = new LinkedHashMap<>();
        for (NeedStatus status : NeedStatus.values()) {
            needsByStatus.put(status.name(), 0L);
        }
        careNeedRepository.countByStatus(run.id).forEach(row -> needsByStatus.put(((NeedStatus) row[0]).name(), (Long) row[1]));
        return EvaluationRunDto.from(run, new EvaluationRunDto.Summary(tiers, needsByStatus));
    }
}
