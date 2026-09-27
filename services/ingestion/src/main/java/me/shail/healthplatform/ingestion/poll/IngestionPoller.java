package me.shail.healthplatform.ingestion.poll;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import me.shail.healthplatform.ingestion.batch.BatchCreator;
import me.shail.healthplatform.ingestion.batch.BatchStatusUpdater;
import me.shail.healthplatform.ingestion.batch.CreatedBatch;
import me.shail.healthplatform.ingestion.event.BatchPublisher;
import me.shail.healthplatform.ingestion.model.BatchCompleted;
import me.shail.healthplatform.ingestion.scan.NewFileFinder;

import java.util.Optional;

import static io.smallrye.mutiny.helpers.spies.Spy.onFailure;

@ApplicationScoped
public class IngestionPoller {
    private final NewFileFinder newFileFinder;
    private final BatchCreator batchCreator;
    private final BatchPublisher batchPublisher;
    private final BatchStatusUpdater batchStatusUpdater;


    public IngestionPoller(NewFileFinder newFileFinder, BatchCreator batchCreator, BatchPublisher batchPublisher, BatchStatusUpdater batchStatusUpdater) {
        this.newFileFinder = newFileFinder;
        this.batchCreator = batchCreator;
        this.batchPublisher = batchPublisher;
        this.batchStatusUpdater = batchStatusUpdater;
    }

    @Scheduled(every = "{ingestion.poll-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public Uni<Void> poll() {
        return newFileFinder.findNewFiles()
                .chain(files -> files.isEmpty()
                        ? Uni.createFrom().item(Optional.<CreatedBatch>empty())
                        : batchCreator.create(files))
                .chain(batch -> batch.isEmpty()
                        ? Uni.createFrom().voidItem()
                        : publishAndMark(batch.get()))
                .onFailure().invoke(e -> Log.errorf(e, "Poll failed"))
                .onFailure().recoverWithNull();
    }

    private Uni<Void> publishAndMark(CreatedBatch batch) {
        return batchPublisher.publish(BatchCompleted.from(batch))
                .onFailure().call(e -> batchStatusUpdater.markFailed(batch.batchId()))
                .chain(() -> batchStatusUpdater
                        .markPublished(batch.batchId())
                        .invoke(() -> Log.infof("Published batch %s with %d files",
                                        batch.batchId(),
                                        batch.files().size()
                                )
                        )
                )
                .onFailure().invoke(e -> Log.errorf(e, "Poll failed for batch %s", batch.batchId()));
    }
}
