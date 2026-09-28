package me.shail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import me.shail.model.EvaluationRun;

/**
 * @param programErrors program files that failed to load in this run
 * @param summary       counts of the run's results; only for a SUCCEEDED run
 */
public record EvaluationRunDto(
        Long runId,
        EvaluationRun.Trigger trigger,
        LocalDate asOfDate,
        EvaluationRun.Status status,
        Long sourceEtlRunId,
        Integer patientsEvaluated,
        List<String> programErrors,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Summary summary) {

    /**
     * @param tiers       patients per program and tier
     * @param needsByStatus care needs per status (MET, SCHEDULED, OVERDUE)
     */
    public record Summary(List<TierCount> tiers, Map<String, Long> needsByStatus) {
    }

    /** @param tierId null for eligible patients no tier matched */
    public record TierCount(String programId, String tierId, long patients) {
    }

    public static EvaluationRunDto from(EvaluationRun run, Summary summary) {
        return new EvaluationRunDto(run.id, run.trigger, run.asOfDate, run.status, run.sourceEtlRunId,
                run.patientsEvaluated,
                run.programErrors == null ? List.of() : List.of(run.programErrors.split("\n")),
                run.errorMessage, run.startedAt, run.finishedAt, summary);
    }
}
