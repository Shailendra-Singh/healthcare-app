package me.shail.healthplatform.ingestion.scan;

import io.vertx.mutiny.core.Vertx;
import me.shail.healthplatform.ingestion.config.IngestionConfig;
import me.shail.healthplatform.ingestion.model.FileType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** File-system half of the scan; plain JUnit with a temporary inbox, no Quarkus or database. */
class InboxScannerTest {

    private static final Duration MIN_AGE = Duration.ofMinutes(5);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static Vertx vertx;

    @TempDir
    Path inbox;

    @BeforeAll
    static void startVertx() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void stopVertx() {
        vertx.closeAndAwait();
    }

    @Test
    void returnsKnownFileOlderThanMinAgeWithItsChecksum() throws Exception {
        Path labs = oldFile("labs.csv", "patient_id,test_name,result_value,result_date\nP1,HbA1c,9.4,2026-07-02\n");

        List<InboxFile> files = scan();

        assertEquals(List.of(new InboxFile(FileType.LABS, labs, sha256Of(labs))), files);
    }

    @Test
    void returnsAllFourTypes() throws Exception {
        for (FileType type : FileType.values()) {
            oldFile(type.fileName(), "header\n");
        }

        List<FileType> types = scan().stream().map(InboxFile::type).sorted().toList();

        assertEquals(List.of(FileType.values()), types);
    }

    @Test
    void ignoresUnknownFileNames() throws Exception {
        oldFile("labs_2026-09-26.csv", "header\n");
        oldFile("notes.txt", "hello\n");

        assertTrue(scan().isEmpty());
    }

    @Test
    void skipsFileNewerThanMinAge() throws Exception {
        Files.writeString(inbox.resolve("patients.csv"), "header\n");   // modified just now

        assertTrue(scan().isEmpty());
    }

    @Test
    void ignoresDirectoryWithKnownName() throws Exception {
        Path dir = Files.createDirectory(inbox.resolve("labs.csv"));
        Files.setLastModifiedTime(dir, FileTime.from(Instant.now().minus(Duration.ofHours(1))));

        assertTrue(scan().isEmpty());
    }

    @Test
    void emptyInboxReturnsNothing() {
        assertTrue(scan().isEmpty());
    }

    @Test
    void checksumCoversWholeFileLargerThanOneChunk() throws Exception {
        // Vert.x reads files in 8 KiB chunks by default; 1 MiB spans many of them.
        String content = "P1,HbA1c,9.4,2026-07-02\n".repeat(45_000);
        Path labs = oldFile("labs.csv", content);

        assertEquals(sha256Of(labs), scan().getFirst().sha256());
    }

    private List<InboxFile> scan() {
        return new InboxScanner(vertx, config(inbox)).scan().await().atMost(TIMEOUT);
    }

    /** Writes a file and backdates it past the minimum age. */
    private Path oldFile(String name, String content) throws IOException {
        Path path = Files.writeString(inbox.resolve(name), content, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(path, FileTime.from(Instant.now().minus(MIN_AGE).minusSeconds(60)));
        return path;
    }

    private static String sha256Of(Path path) throws IOException, NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return HexFormat.of().formatHex(hash);
    }

    private static IngestionConfig config(Path dropDir) {
        return new IngestionConfig() {
            @Override
            public Path dropDir() {
                return dropDir;
            }

            @Override
            public Path storageRoot() {
                return dropDir.resolveSibling("storage");
            }

            @Override
            public Duration pollInterval() {
                return Duration.ofMinutes(5);
            }

            @Override
            public Duration minFileAge() {
                return MIN_AGE;
            }
        };
    }
}
