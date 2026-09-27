package me.shail.healthplatform.ingestion.batch;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import me.shail.healthplatform.ingestion.config.IngestionConfig;
import me.shail.healthplatform.ingestion.entity.Batch;
import me.shail.healthplatform.ingestion.entity.BatchFile;
import me.shail.healthplatform.ingestion.model.BatchStatus;
import me.shail.healthplatform.ingestion.model.FileStatus;
import me.shail.healthplatform.ingestion.model.FileType;
import me.shail.healthplatform.ingestion.scan.InboxFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses the configured storage root (shared with dev); every batch folder a test
 * creates is removed afterwards. Inbox files live in a temporary folder.
 */
@QuarkusTest
class BatchCreatorTest {

    @Inject
    BatchCreator creator;

    @Inject
    IngestionConfig config;

    @TempDir
    Path inbox;

    private Set<String> foldersBefore;

    @BeforeEach
    void rememberStorageFolders() throws IOException {
        Files.createDirectories(config.storageRoot());
        foldersBefore = storageFolders();
    }

    @AfterEach
    void removeCreatedFolders() throws IOException {
        for (String name : storageFolders()) {
            if (!foldersBefore.contains(name)) {
                deleteTree(config.storageRoot().resolve(name));
            }
        }
    }

    @Test
    @RunOnVertxContext
    void copiesFilesAndRecordsBatch(UniAsserter asserter) throws Exception {
        InboxFile labs = inboxFile(FileType.LABS, "patient_id,test_name\nP1,HbA1c\n");
        InboxFile patients = inboxFile(FileType.PATIENTS, "patient_id,first_name\nP1,Ana\n");
        AtomicReference<CreatedBatch> created = new AtomicReference<>();

        asserter.assertThat(() -> creator.create(List.of(labs, patients)), result -> {
            CreatedBatch batch = result.orElseThrow();
            created.set(batch);
            String id = batch.batchId().toString();
            assertEquals(List.of(
                    new StoredFile(FileType.LABS, id + "/labs.csv", labs.sha256()),
                    new StoredFile(FileType.PATIENTS, id + "/patients.csv", patients.sha256())), batch.files());
            assertSameContent(labs.path(), config.storageRoot().resolve(id + "/labs.csv"));
            assertSameContent(patients.path(), config.storageRoot().resolve(id + "/patients.csv"));
            assertEquals(Set.of("labs.csv", "patients.csv"), fileNames(config.storageRoot().resolve(id)));
        });

        asserter.assertThat(() -> Panache.withSession(() -> Batch.<Batch>find("batchId", created.get().batchId()).firstResult()),
                batch -> {
                    assertEquals(BatchStatus.RECEIVED, batch.status);
                    assertNotNull(batch.receivedAt);
                });

        asserter.assertThat(() -> Panache.withSession(() -> BatchFile.<BatchFile>list(
                        "batch.batchId = ?1 order by fileType", created.get().batchId())),
                rows -> {
                    assertEquals(2, rows.size());
                    for (BatchFile row : rows) {
                        StoredFile expected = created.get().files().stream()
                                .filter(f -> f.type() == row.fileType).findFirst().orElseThrow();
                        assertEquals(expected.sha256(), row.sha256);
                        assertEquals(expected.storagePath(), row.storagePath);
                        assertEquals(FileStatus.NEW, row.status);
                    }
                });
    }

    @Test
    @RunOnVertxContext
    void leavesOutFileChangedAfterScan(UniAsserter asserter) throws Exception {
        InboxFile labs = inboxFile(FileType.LABS, "patient_id,test_name\nP1,HbA1c\n");
        InboxFile stale = changedAfterScan(inboxFile(FileType.ENCOUNTERS, "patient_id,specialty\nP1,Cardiology\n"));

        asserter.assertThat(() -> creator.create(List.of(labs, stale)), result -> {
            CreatedBatch batch = result.orElseThrow();
            assertEquals(List.of(FileType.LABS), batch.files().stream().map(StoredFile::type).toList());
            assertEquals(Set.of("labs.csv"), fileNames(config.storageRoot().resolve(batch.batchId().toString())));
        });
    }

    @Test
    @RunOnVertxContext
    void createsNothingWhenEveryFileChangedAfterScan(UniAsserter asserter) throws Exception {
        InboxFile stale = changedAfterScan(inboxFile(FileType.LABS, "patient_id,test_name\nP1,HbA1c\n"));

        asserter.assertThat(() -> creator.create(List.of(stale)), result -> {
            assertTrue(result.isEmpty());
            assertEquals(foldersBefore, storageFolders(), "no batch folder is left behind");
        });
    }

    private InboxFile inboxFile(FileType type, String content) throws IOException, NoSuchAlgorithmException {
        Path path = Files.writeString(inbox.resolve(type.fileName()), content);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return new InboxFile(type, path, HexFormat.of().formatHex(hash));
    }

    /** Simulates the file being overwritten between the scan and the copy. */
    private static InboxFile changedAfterScan(InboxFile scanned) throws IOException {
        Files.writeString(scanned.path(), "overwritten after the scan\n");
        return scanned;
    }

    private static void assertSameContent(Path expected, Path actual) {
        try {
            assertEquals(-1L, Files.mismatch(expected, actual), actual + " differs from " + expected);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private Set<String> storageFolders() {
        return fileNames(config.storageRoot());
    }

    private static Set<String> fileNames(Path dir) {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
