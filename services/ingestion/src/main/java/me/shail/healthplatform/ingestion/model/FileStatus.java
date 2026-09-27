package me.shail.healthplatform.ingestion.model;

/** Lifecycle of a file in a batch; matches the batch_file_status_check constraint. */
public enum FileStatus {
    /** Copied to storage, event not yet published. */
    NEW,
    /** Event published; a file with the same type and checksum is skipped from now on. */
    LOADED
}
