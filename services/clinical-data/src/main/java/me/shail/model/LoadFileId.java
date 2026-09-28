package me.shail.model;

import java.io.Serializable;

/**
 * Primary key of {@link LoadFile}: one row per CSV file per run.
 */
public record LoadFileId(Long runId, String fileName) implements Serializable {
}
