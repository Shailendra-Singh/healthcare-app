package me.shail.healthplatform.ingestion.model;

import java.util.List;
import java.util.UUID;

public record BatchCompleted(UUID batchId, List<BatchCompletedFile> files) {
}
