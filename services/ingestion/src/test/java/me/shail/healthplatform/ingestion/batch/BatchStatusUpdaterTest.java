package me.shail.healthplatform.ingestion.batch;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import me.shail.healthplatform.ingestion.entity.Batch;
import me.shail.healthplatform.ingestion.entity.BatchFile;
import me.shail.healthplatform.ingestion.model.BatchStatus;
import me.shail.healthplatform.ingestion.model.FileStatus;
import me.shail.healthplatform.ingestion.model.FileType;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests BatchStatusUpdater alone: rows are inserted directly in the state Step 7
 * leaves them (batch RECEIVED, files NEW), without copying files or Kafka.
 */
@QuarkusTest
class BatchStatusUpdaterTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    BatchStatusUpdater updater;

    @Test
    @RunOnVertxContext
    void markPublishedSetsBatchPublishedAndFilesLoaded(UniAsserter asserter) {
        UUID batchId = UUID.randomUUID();
        asserter.execute(() -> storeBatch(batchId, FileType.LABS, FileType.PATIENTS));

        asserter.execute(() -> updater.markPublished(batchId));

        asserter.assertThat(() -> findBatch(batchId), batch -> {
            assertEquals(BatchStatus.PUBLISHED, batch.status);
            assertNotNull(batch.completedAt);
        });
        asserter.assertThat(() -> findFiles(batchId), files -> {
            assertEquals(2, files.size());
            files.forEach(file -> assertEquals(FileStatus.LOADED, file.status, file.fileType + " status"));
        });
    }

    @Test
    @RunOnVertxContext
    void markFailedSetsBatchFailedAndLeavesFilesNew(UniAsserter asserter) {
        UUID batchId = UUID.randomUUID();
        asserter.execute(() -> storeBatch(batchId, FileType.LABS, FileType.PATIENTS));

        asserter.execute(() -> updater.markFailed(batchId));

        asserter.assertThat(() -> findBatch(batchId), batch -> {
            assertEquals(BatchStatus.FAILED, batch.status);
            assertNotNull(batch.completedAt);
        });
        asserter.assertThat(() -> findFiles(batchId), files -> {
            assertEquals(2, files.size());
            files.forEach(file -> assertEquals(FileStatus.NEW, file.status, file.fileType + " status"));
        });
    }

    @Test
    @RunOnVertxContext
    void markPublishedSetsBatchPublishedOnlyForItsOwnBatch(UniAsserter asserter) {
        UUID batchId1 = UUID.randomUUID();
        UUID batchId2 = UUID.randomUUID();
        asserter.execute(() -> storeBatch(batchId1, FileType.LABS, FileType.PATIENTS));
        asserter.execute(() -> storeBatch(batchId2, FileType.LABS, FileType.PATIENTS));

        asserter.execute(() -> updater.markPublished(batchId1));

        // First batch published
        asserter.assertThat(() -> findBatch(batchId1), batch -> {
            assertEquals(BatchStatus.PUBLISHED, batch.status);
            assertNotNull(batch.completedAt);
        });

        asserter.assertThat(() -> findFiles(batchId1), files -> {
            assertEquals(2, files.size());
            files.forEach(file -> assertEquals(FileStatus.LOADED, file.status, file.fileType + " status"));
        });

        // Second batch left untouched
        asserter.assertThat(() -> findBatch(batchId2), batch -> {
            assertEquals(BatchStatus.RECEIVED, batch.status);
            assertNull(batch.completedAt);
        });

        asserter.assertThat(() -> findFiles(batchId2), files -> {
            assertEquals(2, files.size());
            files.forEach(file -> assertEquals(FileStatus.NEW, file.status, file.fileType + " status"));
        });
    }

    @Test
    @RunOnVertxContext
    void unknownBatchFails(UniAsserter asserter) {
        asserter.assertFailedWith(() -> updater.markPublished(UUID.randomUUID()), IllegalStateException.class);
    }

    @Test
    @RunOnVertxContext
    void failInterruptedFailsReceivedBatchesOnly(UniAsserter asserter) {
        UUID interrupted = UUID.randomUUID();
        UUID published = UUID.randomUUID();
        asserter.execute(() -> storeBatch(interrupted, FileType.LABS));
        asserter.execute(() -> storeBatch(published, FileType.LABS));
        asserter.execute(() -> updater.markPublished(published));

        asserter.assertThat(() -> updater.failInterrupted(), count -> assertTrue(count >= 1));

        asserter.assertThat(() -> findBatch(interrupted), batch -> {
            assertEquals(BatchStatus.FAILED, batch.status);
            assertNotNull(batch.completedAt);
        });
        asserter.assertThat(() -> findFiles(interrupted),
                files -> assertEquals(FileStatus.NEW, files.getFirst().status, "files stay NEW, so they are retried"));
        asserter.assertThat(() -> findBatch(published),
                batch -> assertEquals(BatchStatus.PUBLISHED, batch.status));
    }

    /** A batch as Step 7 leaves it: RECEIVED, with one NEW file per given type. */
    private static Uni<Void> storeBatch(UUID batchId, FileType... types) {
        return Panache.withTransaction(() -> {
            Batch batch = new Batch();
            batch.batchId = batchId;
            batch.status = BatchStatus.RECEIVED;
            batch.receivedAt = Instant.now();

            List<BatchFile> files = Arrays.stream(types).map(type -> {
                BatchFile file = new BatchFile();
                file.batch = batch;
                file.fileType = type;
                file.sha256 = randomSha256();
                file.storagePath = batchId + "/" + type.fileName();
                file.status = FileStatus.NEW;
                return file;
            }).toList();

            return batch.persist().chain(() -> BatchFile.persist(files));
        });
    }

    /** Fresh session, so the check reads what was committed, not a cached entity. */
    private static Uni<Batch> findBatch(UUID batchId) {
        return Panache.withSession(() -> Batch.<Batch>find("batchId", batchId).firstResult());
    }

    private static Uni<List<BatchFile>> findFiles(UUID batchId) {
        return Panache.withSession(() -> BatchFile.<BatchFile>list("batch.batchId = ?1", batchId));
    }

    private static String randomSha256() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
