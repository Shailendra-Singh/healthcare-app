package me.shail.healthplatform.ingestion.api;

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
import me.shail.healthplatform.ingestion.config.IngestionConfig;
import me.shail.healthplatform.ingestion.entity.Batch;
import me.shail.healthplatform.ingestion.entity.BatchFile;
import me.shail.healthplatform.ingestion.model.BatchStatus;
import me.shail.healthplatform.ingestion.model.FileStatus;
import me.shail.healthplatform.ingestion.model.FileType;
import me.shail.healthplatform.ingestion.poll.TempFoldersProfile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static me.shail.healthplatform.ingestion.ingestion.KafkaTestSupport.awaitRecordWithKey;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The /batches endpoints over HTTP. Uses TempFoldersProfile, so stored copies made
 * for the reprocess tests go to a temporary storage folder, not the real one.
 */
@QuarkusTest
@TestProfile(TempFoldersProfile.class)
@WithTestResource(KafkaCompanionResource.class)
class BatchResourceTest {

    @Inject
    IngestionConfig config;

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Test
    void listsBatchesNewestFirst() throws Throwable {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        storeBatch(older, BatchStatus.PUBLISHED, Instant.now().minus(1, ChronoUnit.HOURS), FileType.LABS);
        storeBatch(newer, BatchStatus.PUBLISHED, Instant.now(), FileType.LABS);

        List<String> ids = given().queryParam("limit", 200)
                .when().get("/batches")
                .then().statusCode(200)
                .extract().jsonPath().getList("batchId");

        assertTrue(ids.indexOf(newer.toString()) >= 0 && ids.indexOf(older.toString()) > ids.indexOf(newer.toString()),
                "newer batch is listed before the older one");
    }

    @Test
    void showsOneBatchWithItsFiles() throws Throwable {
        UUID batchId = UUID.randomUUID();
        storeBatch(batchId, BatchStatus.PUBLISHED, Instant.now(), FileType.LABS, FileType.PATIENTS);

        when().get("/batches/{id}", batchId)
                .then().statusCode(200)
                .body("batchId", equalTo(batchId.toString()))
                .body("status", equalTo("PUBLISHED"))
                .body("receivedAt", notNullValue())
                .body("files.type", containsInAnyOrder("LABS", "PATIENTS"))
                .body("files.path", containsInAnyOrder(batchId + "/labs.csv", batchId + "/patients.csv"))
                .body("files.status", containsInAnyOrder("NEW", "NEW"));
    }

    @Test
    void unknownOrMalformedBatchIdIs404() {
        when().get("/batches/{id}", UUID.randomUUID()).then().statusCode(404)
                .body("error", containsString("not found"));
        when().get("/batches/not-a-uuid").then().statusCode(404);
        when().post("/batches/{id}/reprocess", UUID.randomUUID()).then().statusCode(404);
    }

    @Test
    void reprocessRepublishesFailedBatch() throws Throwable {
        UUID batchId = UUID.randomUUID();
        storeBatch(batchId, BatchStatus.FAILED, Instant.now(), FileType.LABS);
        storeCopy(batchId, FileType.LABS);

        try (ConsumerTask<String, String> task = companion.consumeStrings().fromTopics("batch.completed")) {
            when().post("/batches/{id}/reprocess", batchId)
                    .then().statusCode(200)
                    .body("status", equalTo("PUBLISHED"))
                    .body("completedAt", notNullValue())
                    .body("files.status", containsInAnyOrder("LOADED"));

            awaitRecordWithKey(task, batchId.toString(), Duration.ofSeconds(10));
        }
    }

    @Test
    void reprocessRefusesBatchStillBeingCreated() throws Throwable {
        UUID batchId = UUID.randomUUID();
        storeBatch(batchId, BatchStatus.RECEIVED, Instant.now(), FileType.LABS);

        when().post("/batches/{id}/reprocess", batchId)
                .then().statusCode(409)
                .body("error", containsString("still being created"));
    }

    @Test
    void reprocessRefusesBatchWhoseCopiesAreGone() throws Throwable {
        UUID batchId = UUID.randomUUID();
        storeBatch(batchId, BatchStatus.FAILED, Instant.now(), FileType.LABS);   // no copy in storage

        when().post("/batches/{id}/reprocess", batchId)
                .then().statusCode(409)
                .body("error", containsString("missing"));

        when().get("/batches/{id}", batchId)
                .then().body("status", equalTo("FAILED"));
    }

    /** Batch rows only; file rows are NEW with paths "<batchId>/<file name>". */
    private static void storeBatch(UUID batchId, BatchStatus status, Instant receivedAt, FileType... types)
            throws Throwable {
        VertxContextSupport.subscribeAndAwait(() -> Panache.withTransaction(() -> {
            Batch batch = new Batch();
            batch.batchId = batchId;
            batch.status = status;
            batch.receivedAt = receivedAt;
            List<BatchFile> files = Arrays.stream(types).map(type -> {
                BatchFile file = new BatchFile();
                file.batch = batch;
                file.fileType = type;
                file.sha256 = "0".repeat(64);
                file.storagePath = batchId + "/" + type.fileName();
                file.status = FileStatus.NEW;
                return file;
            }).toList();
            return batch.persist().chain(() -> BatchFile.persist(files));
        }));
    }

    /** The stored copy reprocessing re-publishes, in the profile's temporary storage. */
    private void storeCopy(UUID batchId, FileType type) throws IOException {
        Path copy = config.storageRoot().resolve(batchId + "/" + type.fileName());
        Files.createDirectories(copy.getParent());
        Files.writeString(copy, "header\n");
    }
}
