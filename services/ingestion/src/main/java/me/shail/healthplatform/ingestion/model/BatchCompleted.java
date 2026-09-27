package me.shail.healthplatform.ingestion.model;

import me.shail.healthplatform.ingestion.batch.CreatedBatch;

import java.util.List;
import java.util.UUID;

public record BatchCompleted(UUID batchId, List<BatchCompletedFile> files) {
    public static BatchCompleted from(CreatedBatch createdBatch){
        return new BatchCompleted(createdBatch.batchId(),
                createdBatch
                        .files().stream()
                        .map(f -> new BatchCompletedFile(
                                f.type(),
                                f.storagePath())
                        ).toList());
    }
}
