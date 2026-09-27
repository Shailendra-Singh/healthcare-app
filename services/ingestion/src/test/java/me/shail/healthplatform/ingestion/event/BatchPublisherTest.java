package me.shail.healthplatform.ingestion.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;
import me.shail.healthplatform.ingestion.batch.CreatedBatch;
import me.shail.healthplatform.ingestion.batch.StoredFile;
import me.shail.healthplatform.ingestion.model.BatchCompleted;
import me.shail.healthplatform.ingestion.model.FileType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static me.shail.healthplatform.ingestion.ingestion.KafkaTestSupport.awaitRecordWithKey;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@WithTestResource(KafkaCompanionResource.class)
public class BatchPublisherTest {
    @Inject
    BatchPublisher publisher;
    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Test
    public void checkKafkaEventPublished() throws JsonProcessingException, InterruptedException {
        UUID batchId = UUID.randomUUID();
        CreatedBatch createdBatch = new CreatedBatch(batchId, List.of(
                new StoredFile(FileType.LABS, batchId + "/labs.csv",
                        "6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b"),
                new StoredFile(FileType.DIAGNOSES, batchId + "/diagnoses.csv",
                        "d4735e3a265e16eee03f59718b9b5d03019c07d8b6c51f90da3a666eec13ab35")));

        ObjectMapper mapper = new ObjectMapper();

        BatchCompleted batchCompleted = BatchCompleted.from(createdBatch);
        publisher.publish(batchCompleted).await().atMost(Duration.ofSeconds(10));
        String expected = mapper.writeValueAsString(batchCompleted);

        try (ConsumerTask<String, String> task = companion.consumeStrings().fromTopics("batch.completed")) {
            ConsumerRecord<String, String> record = awaitRecordWithKey(task, batchId.toString(), Duration.ofSeconds(10));
            String actual = record.value();
            assertEquals(mapper.readTree(expected), mapper.readTree(actual));
        }
    }
}
