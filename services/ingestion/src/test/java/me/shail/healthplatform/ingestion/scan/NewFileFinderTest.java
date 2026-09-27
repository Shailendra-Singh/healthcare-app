package me.shail.healthplatform.ingestion.scan;

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

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Database half of the scan, against the Dev Services Postgres. Each test uses
 * random checksums, so rows left by other tests never match.
 */
@QuarkusTest
class NewFileFinderTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    NewFileFinder finder;

    @Test
    @RunOnVertxContext
    void dropsFileMatchingLoadedTypeAndChecksum(UniAsserter asserter) {
        String loaded = randomSha256();
        String fresh = randomSha256();
        asserter.execute(() -> storeFile(FileType.LABS, loaded, FileStatus.LOADED));

        asserter.assertThat(
                () -> finder.withoutLoaded(List.of(inboxFile(FileType.LABS, loaded), inboxFile(FileType.LABS, fresh))),
                result -> assertEquals(List.of(inboxFile(FileType.LABS, fresh)), result));
    }

    @Test
    @RunOnVertxContext
    void keepsFileWhoseChecksumIsOnlyRecordedAsNew(UniAsserter asserter) {
        String sha = randomSha256();
        asserter.execute(() -> storeFile(FileType.LABS, sha, FileStatus.NEW));

        asserter.assertThat(
                () -> finder.withoutLoaded(List.of(inboxFile(FileType.LABS, sha))),
                result -> assertEquals(List.of(inboxFile(FileType.LABS, sha)), result));
    }

    @Test
    @RunOnVertxContext
    void keepsSameChecksumLoadedUnderAnotherType(UniAsserter asserter) {
        String sha = randomSha256();
        asserter.execute(() -> storeFile(FileType.PATIENTS, sha, FileStatus.LOADED));

        asserter.assertThat(
                () -> finder.withoutLoaded(List.of(inboxFile(FileType.LABS, sha))),
                result -> assertEquals(List.of(inboxFile(FileType.LABS, sha)), result));
    }

    @Test
    @RunOnVertxContext
    void emptyInputReturnsEmpty(UniAsserter asserter) {
        asserter.assertThat(
                () -> finder.withoutLoaded(List.of()),
                result -> assertEquals(List.of(), result));
    }

    private static Uni<Void> storeFile(FileType type, String sha256, FileStatus status) {
        return Panache.withTransaction(() -> {
            Batch batch = new Batch();
            batch.batchId = UUID.randomUUID();
            batch.status = BatchStatus.PUBLISHED;
            batch.receivedAt = Instant.now();
            return batch.persist().chain(() -> {
                BatchFile file = new BatchFile();
                file.batch = batch;
                file.fileType = type;
                file.sha256 = sha256;
                file.storagePath = batch.batchId + "/" + type.fileName();
                file.status = status;
                return file.persist();
            });
        }).replaceWithVoid();
    }

    private static InboxFile inboxFile(FileType type, String sha256) {
        return new InboxFile(type, Path.of("/inbox", type.fileName()), sha256);
    }

    private static String randomSha256() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
