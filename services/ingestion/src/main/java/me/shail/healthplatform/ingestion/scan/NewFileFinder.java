package me.shail.healthplatform.ingestion.scan;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import me.shail.healthplatform.ingestion.entity.BatchFile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Database half of a poll: drops inbox files whose type and checksum match a
 * LOADED file. An empty result means there is nothing to do.
 */
@ApplicationScoped
public class NewFileFinder {

    private final InboxScanner scanner;

    NewFileFinder(InboxScanner scanner) {
        this.scanner = scanner;
    }

    @WithSession
    public Uni<List<InboxFile>> findNewFiles() {
        return scanner.scan().chain(this::withoutLoaded);
    }

    /** Keeps the files whose type and checksum match no LOADED file. */
    @WithSession
    Uni<List<InboxFile>> withoutLoaded(List<InboxFile> files) {
        if (files.isEmpty()) {
            return Uni.createFrom().item(files);
        }
        List<String> checksums = files.stream().map(InboxFile::sha256).toList();
        return BatchFile.findLoaded(checksums).map(loaded -> {
            Set<String> loadedKeys = loaded.stream()
                    .map(f -> key(f.fileType.name(), f.sha256))
                    .collect(Collectors.toSet());
            return files.stream()
                    .filter(f -> !loadedKeys.contains(key(f.type().name(), f.sha256())))
                    .toList();
        });
    }

    private static String key(String type, String sha256) {
        return type + ':' + sha256;
    }
}
