package me.shail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import me.shail.model.GenerationRun;

/** @param evaluationRunId the rules-engine evaluation run reconciled against */
public record GenerationRunDto(
        Long runId,
        GenerationRun.Trigger trigger,
        Long evaluationRunId,
        GenerationRun.Status status,
        Integer needsRead,
        Integer tasksCreated,
        Integer tasksUpdated,
        Integer tasksClosed,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt) {

    public static GenerationRunDto from(GenerationRun run) {
        return new GenerationRunDto(run.id, run.trigger, run.evaluationRunId, run.status, run.needsRead,
                run.tasksCreated, run.tasksUpdated, run.tasksClosed, run.errorMessage, run.startedAt, run.finishedAt);
    }
}
