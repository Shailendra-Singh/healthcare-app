package me.shail.healthplatform.ingestion.batch;

import java.util.List;
import java.util.UUID;

/** A recorded batch with status RECEIVED: what the batch.completed event will carry. */
public record CreatedBatch(UUID batchId, List<StoredFile> files) {
}
