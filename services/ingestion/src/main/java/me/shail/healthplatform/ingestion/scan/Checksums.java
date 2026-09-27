package me.shail.healthplatform.ingestion.scan;

import io.smallrye.mutiny.Uni;
import io.vertx.core.file.OpenOptions;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.file.FileSystem;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 of a file, used for inbox files and their copies alike. */
public final class Checksums {

    private Checksums() {
    }

    /** Streams the file through SHA-256 chunk by chunk, so memory stays flat. Returns lowercase hex. */
    public static Uni<String> sha256(FileSystem fs, Path path) {
        return fs.open(path.toString(), new OpenOptions().setRead(true))
                .onItem().transformToUni(file -> file.toMulti()
                        .collect().in(Checksums::newDigest, Checksums::update)
                        .map(digest -> HexFormat.of().formatHex(digest.digest()))
                        .eventually(file::close));
    }

    private static void update(MessageDigest digest, Buffer chunk) {
        digest.update(chunk.getBytes());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every JVM", e);
        }
    }
}
