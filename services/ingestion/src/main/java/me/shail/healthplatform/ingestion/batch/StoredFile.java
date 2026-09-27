package me.shail.healthplatform.ingestion.batch;

import me.shail.healthplatform.ingestion.model.FileType;

/**
 * A file copied into a batch; storagePath is relative to the storage root, e.g. "<batchId>/labs.csv".
 */
public record StoredFile(FileType type, String storagePath, String sha256) {
}
