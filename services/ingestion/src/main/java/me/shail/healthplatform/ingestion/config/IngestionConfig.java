package me.shail.healthplatform.ingestion.config;

import io.smallrye.config.ConfigMapping;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Ingestion settings from the {@code ingestion.*} keys in application.properties.
 * Quarkus validates them at startup: a missing or malformed value fails the boot.
 */
@ConfigMapping(prefix = "ingestion")
public interface IngestionConfig {

    /** Folder where raw CSV files arrive; ingestion only reads from it. */
    Path dropDir();

    /** Folder where validated files and rejected-row reports are written for clinical data to read. */
    Path storageRoot();

    /** How often the drop folder is polled. */
    Duration pollInterval();

    /** Minimum time since a file's last modification before it is picked up. */
    Duration minFileAge();
}
