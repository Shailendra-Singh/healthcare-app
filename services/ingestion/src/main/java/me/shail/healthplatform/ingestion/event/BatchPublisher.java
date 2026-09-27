package me.shail.healthplatform.ingestion.event;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import me.shail.healthplatform.ingestion.model.BatchCompleted;
import org.eclipse.microprofile.reactive.messaging.Channel;
import io.smallrye.reactive.messaging.kafka.Record;

@ApplicationScoped
public class BatchPublisher {
    private final MutinyEmitter<Record<String, BatchCompleted>> emitter;

    public BatchPublisher(@Channel("batch-completed") MutinyEmitter<Record<String, BatchCompleted>> emitter){
        this.emitter = emitter;
    }

    public Uni<Void> publish(BatchCompleted event){
        var record = Record.of(event.batchId().toString(), event);
        return emitter.send(record);
    }
}
