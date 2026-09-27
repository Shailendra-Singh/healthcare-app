package me.shail.healthplatform.ingestion.batch;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import me.shail.healthplatform.ingestion.model.BatchStatus;

@QuarkusTest
public class BatchStatusUpdaterTest {
    @Inject BatchStatusUpdater updater;

    public void whenMarkPublished_StatusIsPublishedAndLoaded(){

    }

}
