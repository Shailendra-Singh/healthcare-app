package me.shail.healthplatform.ingestion.ingestion;

import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.time.Duration;
import java.util.Optional;

public class KafkaTestSupport {
    public static ConsumerRecord<String, String> awaitRecordWithKey(ConsumerTask<String, String> task,
                                                              String key,
                                                              Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<ConsumerRecord<String, String>> match = task.getRecords().stream()
                    .filter(r -> key.equals(r.key()))
                    .findFirst();
            if (match.isPresent()) {
                return match.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No record with key " + key + " on the topic within " + timeout);
    }
}
