package me.shail.dto;

import java.time.OffsetDateTime;
import java.util.List;
import me.shail.model.LoadRun;

/**
 * An ETL run with its files.
 *
 * @param status       STARTED, LOADED, SUCCEEDED, FAILED or SKIPPED
 * @param rejectedRows total over all files
 */
public record LoadRunDto(
        Long runId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String errorMessage,
        long rejectedRows,
        List<LoadFileDto> files) {

    public static LoadRunDto from(LoadRun run, List<LoadFileDto> files) {
        return run == null ? null
                : new LoadRunDto(
                        run.id,
                        run.status,
                        run.startedAt,
                        run.finishedAt,
                        run.errorMessage,
                        files.stream().mapToLong(LoadFileDto::rejectedRows).sum(),
                        files);
    }
}
