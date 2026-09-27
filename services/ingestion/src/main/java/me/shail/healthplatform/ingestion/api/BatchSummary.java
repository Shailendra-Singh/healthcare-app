package me.shail.healthplatform.ingestion.api;

import me.shail.healthplatform.ingestion.entity.Batch;
import me.shail.healthplatform.ingestion.model.BatchStatus;

import java.time.Instant;
import java.util.UUID;

/** One row of GET /batches. */
public record BatchSummary(UUID batchId, BatchStatus status, Instant receivedAt, Instant completedAt) {

    static BatchSummary of(Batch batch) {
        return new BatchSummary(batch.batchId, batch.status, batch.receivedAt, batch.completedAt);
    }
}
