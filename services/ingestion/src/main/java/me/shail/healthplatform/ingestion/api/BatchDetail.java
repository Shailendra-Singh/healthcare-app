package me.shail.healthplatform.ingestion.api;

import me.shail.healthplatform.ingestion.entity.Batch;
import me.shail.healthplatform.ingestion.entity.BatchFile;
import me.shail.healthplatform.ingestion.model.BatchStatus;
import me.shail.healthplatform.ingestion.model.FileStatus;
import me.shail.healthplatform.ingestion.model.FileType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** GET /batches/{batchId}: a batch and its files. */
public record BatchDetail(UUID batchId, BatchStatus status, Instant receivedAt, Instant completedAt,
                          List<File> files) {

    /** path is relative to the storage root. */
    public record File(FileType type, String path, String sha256, FileStatus status) {
    }

    static BatchDetail of(Batch batch, List<BatchFile> files) {
        return new BatchDetail(batch.batchId, batch.status, batch.receivedAt, batch.completedAt,
                files.stream().map(f -> new File(f.fileType, f.storagePath, f.sha256, f.status)).toList());
    }
}
