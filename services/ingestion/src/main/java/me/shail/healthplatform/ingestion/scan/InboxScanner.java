package me.shail.healthplatform.ingestion.scan;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.file.FileSystem;
import jakarta.enterprise.context.ApplicationScoped;
import me.shail.healthplatform.ingestion.config.IngestionConfig;
import me.shail.healthplatform.ingestion.model.FileType;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * File-system half of a poll: lists the drop folder, keeps the four known files
 * that are at least the minimum age, and checksums them. No database access.
 */
@ApplicationScoped
public class InboxScanner {

    private final FileSystem fs;
    private final IngestionConfig config;

    InboxScanner(Vertx vertx, IngestionConfig config) {
        this.fs = vertx.fileSystem();
        this.config = config;
    }

    public Uni<List<InboxFile>> scan() {
        Instant cutoff = Instant.now().minus(config.minFileAge());
        return fs.readDir(config.dropDir().toString())
                .onItem().transformToMulti(entries -> Multi.createFrom().iterable(entries))
                .map(Path::of)
                .map(this::toCandidate)
                .select().where(Optional::isPresent)
                .map(Optional::get)
                .select().when(candidate -> isOldEnough(candidate.path(), cutoff))
                .onItem().transformToUniAndConcatenate(candidate -> Checksums.sha256(fs, candidate.path())
                        .map(hash -> new InboxFile(candidate.type(), candidate.path(), hash)))
                .collect().asList();
    }

    private Optional<Candidate> toCandidate(Path path) {
        Optional<FileType> type = FileType.fromFileName(path.getFileName().toString());
        if (type.isEmpty()) {
            Log.debugf("Ignoring unknown file %s", path);
        }
        return type.map(t -> new Candidate(t, path));
    }

    /**
     * Regular file whose last modification is at or before the cutoff.
     */
    private Uni<Boolean> isOldEnough(Path path, Instant cutoff) {
        return fs.props(path.toString()).map(props -> {
            boolean oldEnough = !Instant.ofEpochMilli(props.lastModifiedTime()).isAfter(cutoff);
            if (props.isRegularFile() && !oldEnough) {
                Log.debugf("Skipping %s until it is older than %s", path, config.minFileAge());
            }
            return props.isRegularFile() && oldEnough;
        });
    }

    private record Candidate(FileType type, Path path) {
    }
}
