package me.shail.healthplatform.ingestion.batch;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import me.shail.healthplatform.ingestion.entity.Batch;
import me.shail.healthplatform.ingestion.entity.BatchFile;
import me.shail.healthplatform.ingestion.model.BatchStatus;
import me.shail.healthplatform.ingestion.model.FileStatus;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class BatchStatusUpdater {

    @WithTransaction
    public Uni<Void> markPublished(UUID batchId) {
        return Batch.<Batch>find("batchId", batchId)
                .firstResult()
                .onItem()
                .ifNull().failWith(() -> new IllegalStateException("Batch " + batchId + " not found"))
                .invoke(batch -> {
                    batch.completedAt = Instant.now();
                    batch.status = BatchStatus.PUBLISHED;
                })
                .chain(() -> BatchFile.update("status = ?1 where batch.batchId = ?2", FileStatus.LOADED, batchId))
                .replaceWithVoid();
    }

    @WithTransaction
    public Uni<Void> markFailed(UUID batchId) {
        return Batch.<Batch>find("batchId", batchId)
                .firstResult()
                .onItem()
                .ifNotNull()
                .invoke(batch -> {
                    batch.completedAt = Instant.now();
                    batch.status = BatchStatus.FAILED;
                })
                .replaceWithVoid();
    }
}
