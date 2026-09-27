package me.shail.healthplatform.ingestion.poll;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class TempFoldersProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        try {
            Path tempInboxPath = Files.createTempDirectory("ingestion-inbox");
            Path tempStoragePath = Files.createTempDirectory("ingestion-storage");
            return Map.of(
                    "ingestion.drop-dir", tempInboxPath.toString(),
                    "ingestion.storage-root", tempStoragePath.toString()
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
