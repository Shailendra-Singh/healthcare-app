package me.shail.healthplatform.ingestion.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import me.shail.healthplatform.ingestion.model.FileStatus;
import me.shail.healthplatform.ingestion.model.FileType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Collection;
import java.util.List;

/** One new file copied into a batch. */
@Entity
@Table(name = "batch_file")
public class BatchFile extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_ref")
    public Batch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type")
    public FileType fileType;

    /** Hex SHA-256 of the file; the column is CHAR(64). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    public String sha256;

    /** Path of the copy, relative to the storage root. */
    @Column(name = "storage_path")
    public String storagePath;

    @Enumerated(EnumType.STRING)
    public FileStatus status;

    /** LOADED files whose checksum is one of the given ones. */
    public static Uni<List<BatchFile>> findLoaded(Collection<String> checksums) {
        return list("status = ?1 and sha256 in ?2", FileStatus.LOADED, checksums);
    }
}
