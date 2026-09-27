package me.shail.healthplatform.ingestion.poll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;
import me.shail.healthplatform.ingestion.batch.BatchCreator;
import me.shail.healthplatform.ingestion.config.IngestionConfig;
import me.shail.healthplatform.ingestion.entity.Batch;
import me.shail.healthplatform.ingestion.entity.BatchFile;
import me.shail.healthplatform.ingestion.model.BatchStatus;
import me.shail.healthplatform.ingestion.model.FileStatus;
import me.shail.healthplatform.ingestion.model.FileType;
import me.shail.healthplatform.ingestion.scan.NewFileFinder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static me.shail.healthplatform.ingestion.ingestion.KafkaTestSupport.awaitRecordWithKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole poll, from inbox files to Kafka and the database. TempFoldersProfile gives
 * this class its own inbox and storage, so the real data folders are never touched.
 */
@QuarkusTest
@TestProfile(TempFoldersProfile.class)
@WithTestResource(KafkaCompanionResource.class)
class IngestionPollerTest {

    private static final String TOPIC = "batch.completed";
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(10);
    /** How long a poll that should publish nothing is given to prove it. */
    private static final Duration QUIET_PERIOD = Duration.ofSeconds(3);

    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    IngestionPoller poller;

    @Inject
    IngestionConfig config;

    @Inject
    NewFileFinder finder;

    @Inject
    BatchCreator creator;

    @InjectKafkaCompanion
    KafkaCompanion companion;

    /** All tests share the profile's folders, so each one starts from empty ones. */
    @BeforeEach
    void emptyFolders() throws IOException {
        clear(config.dropDir());
        clear(config.storageRoot());
    }

    @Test
    void emptyInboxPublishesNothing() throws Throwable {
        poll();

        assertTrue(batchFolders().isEmpty(), "no batch folder is created");
    }

    @Test
    void firstPollPublishesOneEventForNewFiles() throws Throwable {
        writeInboxFile(FileType.LABS);
        writeInboxFile(FileType.PATIENTS);

        try (ConsumerTask<String, String> task = companion.consumeStrings().fromTopics(TOPIC)) {
            poll();

            UUID batchId = onlyBatchFolder();
            JsonNode event = eventFor(task, batchId);
            assertEquals(Set.of(FileType.LABS, FileType.PATIENTS), fileTypes(event));
            assertEquals(Set.of(batchId + "/labs.csv", batchId + "/patients.csv"), filePaths(event));

            Batch batch = findBatch(batchId);
            assertEquals(BatchStatus.PUBLISHED, batch.status);
            findFiles(batchId).forEach(file ->
                    assertEquals(FileStatus.LOADED, file.status, file.fileType + " status"));
        }
    }

    @Test
    void secondPollWithSameFilesPublishesNothing() throws Throwable {
        writeInboxFile(FileType.LABS);

        try (ConsumerTask<String, String> task = companion.consumeStrings().fromTopics(TOPIC)) {
            poll();
            eventFor(task, onlyBatchFolder());
            int eventsAfterFirstPoll = task.getRecords().size();

            poll();

            Thread.sleep(QUIET_PERIOD.toMillis());
            assertEquals(eventsAfterFirstPoll, task.getRecords().size(), "no new event on the topic");
            assertEquals(1, batchFolders().size(), "no new batch folder");
        }
    }

    @Test
    void changedFileIsPublishedAlone() throws Throwable {
        writeInboxFile(FileType.LABS);
        writeInboxFile(FileType.PATIENTS);

        try (ConsumerTask<String, String> task = companion.consumeStrings().fromTopics(TOPIC)) {
            poll();
            UUID firstBatch = onlyBatchFolder();
            eventFor(task, firstBatch);

            writeInboxFile(FileType.LABS);   // new content, so a new checksum
            poll();

            Set<UUID> newBatches = batchFolders();
            newBatches.remove(firstBatch);
            assertEquals(1, newBatches.size(), "exactly one new batch");
            UUID secondBatch = newBatches.iterator().next();
            assertEquals(Set.of(FileType.LABS), fileTypes(eventFor(task, secondBatch)));
        }
    }

    @Test
    void interruptedBatchIsFailedAndItsFilesPublishedAgain() throws Throwable {
        writeInboxFile(FileType.LABS);
        // A poll that stopped after recording its batch: RECEIVED, files NEW, never published.
        UUID interrupted = VertxContextSupport.subscribeAndAwait(
                () -> finder.findNewFiles().chain(creator::create)).orElseThrow().batchId();

        try (ConsumerTask<String, String> task = companion.consumeStrings().fromTopics(TOPIC)) {
            poll();

            assertEquals(BatchStatus.FAILED, findBatch(interrupted).status);
            Set<UUID> newBatches = batchFolders();
            newBatches.remove(interrupted);
            assertEquals(1, newBatches.size(), "the file is published in a new batch");
            UUID retry = newBatches.iterator().next();
            assertEquals(Set.of(FileType.LABS), fileTypes(eventFor(task, retry)));
            assertEquals(BatchStatus.PUBLISHED, findBatch(retry).status);
        }
    }

    /** Runs a poll on a Vert.x context (reactive Panache needs one) and waits for it. */
    private void poll() throws Throwable {
        VertxContextSupport.subscribeAndAwait(() -> poller.poll());
    }

    /** Unique content every time, so no checksum left by another test can match. */
    private void writeInboxFile(FileType type) throws IOException {
        Files.writeString(config.dropDir().resolve(type.fileName()),
                "header\n" + UUID.randomUUID() + "\n");
    }

    private JsonNode eventFor(ConsumerTask<String, String> task, UUID batchId) throws Exception {
        ConsumerRecord<String, String> record = awaitRecordWithKey(task, batchId.toString(), EVENT_TIMEOUT);
        return mapper.readTree(record.value());
    }

    private static Set<FileType> fileTypes(JsonNode event) {
        return files(event).map(f -> FileType.valueOf(f.get("type").asText())).collect(Collectors.toSet());
    }

    private static Set<String> filePaths(JsonNode event) {
        return files(event).map(f -> f.get("path").asText()).collect(Collectors.toSet());
    }

    private static Stream<JsonNode> files(JsonNode event) {
        return StreamSupport.stream(event.get("files").spliterator(), false);
    }

    /** Batch folders in storage are named by batch ID. */
    private Set<UUID> batchFolders() throws IOException {
        try (Stream<Path> entries = Files.list(config.storageRoot())) {
            return entries.map(p -> UUID.fromString(p.getFileName().toString()))
                    .collect(Collectors.toSet());
        }
    }

    private UUID onlyBatchFolder() throws IOException {
        Set<UUID> folders = batchFolders();
        assertEquals(1, folders.size(), "exactly one batch folder");
        return folders.iterator().next();
    }

    private static Batch findBatch(UUID batchId) throws Throwable {
        return VertxContextSupport.subscribeAndAwait(() -> Panache.withSession(
                () -> Batch.<Batch>find("batchId", batchId).firstResult()));
    }

    private static List<BatchFile> findFiles(UUID batchId) throws Throwable {
        return VertxContextSupport.subscribeAndAwait(() -> Panache.withSession(
                () -> BatchFile.<BatchFile>list("batch.batchId = ?1", batchId)));
    }

    private static void clear(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!p.equals(dir)) {
                    Files.delete(p);
                }
            }
        }
    }
}
