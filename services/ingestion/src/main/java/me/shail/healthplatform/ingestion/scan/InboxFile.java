package me.shail.healthplatform.ingestion.scan;

import me.shail.healthplatform.ingestion.model.FileType;

import java.nio.file.Path;

/**
 * A known file in the inbox, old enough to pick up, with its SHA-256 (hex).
 */
public record InboxFile(FileType type, Path path, String sha256) {
}
