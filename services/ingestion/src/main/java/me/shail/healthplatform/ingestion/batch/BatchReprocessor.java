package me.shail.healthplatform.ingestion.batch;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.file.FileSystem;
import jakarta.enterprise.context.ApplicationScoped;
import me.shail.healthplatform.ingestion.config.IngestionConfig;
import me.shail.healthplatform.ingestion.entity.Batch;
import me.shail.healthplatform.ingestion.entity.BatchFile;
import me.shail.healthplatform.ingestion.event.BatchPublisher;
import me.shail.healthplatform.ingestion.model.BatchCompleted;
import me.shail.healthplatform.ingestion.model.BatchStatus;

import java.util.List;
import java.util.UUID;

/**
 * Re-publishes an existing batch's batch.completed event from its stored copies,
 * then marks it PUBLISHED and its files LOADED. Used to retry a FAILED batch.
 */
@ApplicationScoped
public class BatchReprocessor {

    public enum Outcome {
        REPUBLISHED,
        NOT_FOUND,
        /** Still RECEIVED: a poll is working on it right now. */
        IN_PROGRESS,
        /** A stored copy is gone, so clinical data could not load the batch. */
        FILES_MISSING
    }

    private final FileSystem fs;
    private final IngestionConfig config;
    private final BatchPublisher publisher;
    private final BatchStatusUpdater statusUpdater;

    BatchReprocessor(Vertx vertx, IngestionConfig config, BatchPublisher publisher, BatchStatusUpdater statusUpdater) {
        this.fs = vertx.fileSystem();
        this.config = config;
        this.publisher = publisher;
        this.statusUpdater = statusUpdater;
    }

    /** Fails if the event cannot be published; the batch's status is then left unchanged. */
    @WithSession
    public Uni<Outcome> reprocess(UUID batchId) {
        return Batch.<Batch>find("batchId", batchId).firstResult().chain(batch -> {
            if (batch == null) {
                return Uni.createFrom().item(Outcome.NOT_FOUND);
            }
            if (batch.status == BatchStatus.RECEIVED) {
                return Uni.createFrom().item(Outcome.IN_PROGRESS);
            }
            return BatchFile.<BatchFile>list("batch.id = ?1", batch.id)
                    .map(files -> files.stream()
                            .map(f -> new StoredFile(f.fileType, f.storagePath, f.sha256))
                            .toList())
                    .chain(stored -> allExist(stored).chain(exist -> exist
                            ? republish(new CreatedBatch(batchId, stored))
                            : Uni.createFrom().item(Outcome.FILES_MISSING)));
        });
    }

    private Uni<Outcome> republish(CreatedBatch batch) {
        return publisher.publish(BatchCompleted.from(batch))
                .chain(() -> statusUpdater.markPublished(batch.batchId()))
                .replaceWith(Outcome.REPUBLISHED);
    }

    private Uni<Boolean> allExist(List<StoredFile> files) {
        return Multi.createFrom().iterable(files)
                .onItem().transformToUniAndConcatenate(f ->
                        fs.exists(config.storageRoot().resolve(f.storagePath()).toString()))
                .collect().asList()
                .map(results -> results.stream().allMatch(Boolean::booleanValue));
    }
}
