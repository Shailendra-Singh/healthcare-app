package me.shail.healthplatform.ingestion.batch;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.file.FileSystem;
import jakarta.enterprise.context.ApplicationScoped;
import me.shail.healthplatform.ingestion.config.IngestionConfig;
import me.shail.healthplatform.ingestion.entity.Batch;
import me.shail.healthplatform.ingestion.entity.BatchFile;
import me.shail.healthplatform.ingestion.model.BatchStatus;
import me.shail.healthplatform.ingestion.model.FileStatus;
import me.shail.healthplatform.ingestion.scan.Checksums;
import me.shail.healthplatform.ingestion.scan.InboxFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns new inbox files into a batch: copies them to storage/<batchId>/, then
 * records the batch (RECEIVED) and its files (NEW) in one transaction.
 * Files are copied before any row is written, so a row never points at a missing file.
 */
@ApplicationScoped
public class BatchCreator {

    private final FileSystem fs;
    private final IngestionConfig config;

    BatchCreator(Vertx vertx, IngestionConfig config) {
        this.fs = vertx.fileSystem();
        this.config = config;
    }

    /**
     * Empty when no file could be taken: each one changed after it was scanned.
     * On failure the batch folder is removed and nothing is recorded, so the next poll retries.
     */
    public Uni<Optional<CreatedBatch>> create(List<InboxFile> files) {
        UUID batchId = UUID.randomUUID();
        String batchDir = config.storageRoot().resolve(batchId.toString()).toString();
        return fs.mkdirs(batchDir)
                .chain(() -> Multi.createFrom().iterable(files)
                        .onItem().transformToUniAndConcatenate(file -> copy(file, batchId))
                        .select().where(Optional::isPresent)
                        .map(Optional::get)
                        .collect().asList())
                .chain(stored -> stored.isEmpty()
                        ? deleteDir(batchDir).replaceWith(Optional.<CreatedBatch>empty())
                        : record(batchId, stored).replaceWith(Optional.of(new CreatedBatch(batchId, stored))))
                .onFailure().call(() -> deleteDir(batchDir));
    }

    /**
     * Copies under a temporary name, checks the copy against the scanned checksum,
     * then renames it. A file overwritten since the scan is left for the next poll.
     */
    private Uni<Optional<StoredFile>> copy(InboxFile file, UUID batchId) {
        String relativePath = batchId + "/" + file.type().fileName();
        String target = config.storageRoot().resolve(relativePath).toString();
        String temp = target + ".tmp";
        return fs.copy(file.path().toString(), temp)
                .chain(() -> Checksums.sha256(fs, Path.of(temp)))
                .chain(copied -> {
                    if (!copied.equals(file.sha256())) {
                        Log.warnf("%s changed after it was scanned; leaving it for the next poll", file.path());
                        return fs.delete(temp).replaceWith(Optional.<StoredFile>empty());
                    }
                    return fs.move(temp, target)
                            .replaceWith(Optional.of(new StoredFile(file.type(), relativePath, copied)));
                });
    }

    private Uni<Void> record(UUID batchId, List<StoredFile> stored) {
        return Panache.withTransaction(() -> {
            Batch batch = new Batch();
            batch.batchId = batchId;
            batch.status = BatchStatus.RECEIVED;
            batch.receivedAt = Instant.now();

            List<BatchFile> rows = stored.stream().map(s -> {
                BatchFile row = new BatchFile();
                row.batch = batch;
                row.fileType = s.type();
                row.sha256 = s.sha256();
                row.storagePath = s.storagePath();
                row.status = FileStatus.NEW;
                return row;
            }).toList();

            return batch.persist().chain(() -> BatchFile.persist(rows));
        });
    }

    /**
     * Best effort: a leftover folder is harmless because no row points at it.
     */
    private Uni<Void> deleteDir(String dir) {
        return fs.deleteRecursive(dir, true)
                .onFailure().invoke(e -> Log.warnf(e, "Could not remove %s", dir))
                .onFailure().recoverWithNull();
    }
}
