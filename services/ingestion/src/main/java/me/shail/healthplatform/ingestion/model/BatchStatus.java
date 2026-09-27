package me.shail.healthplatform.ingestion.model;

/**
 * Lifecycle of a batch; matches the batch_status_check constraint.
 */
public enum BatchStatus {
    RECEIVED,
    PUBLISHED,
    FAILED
}
