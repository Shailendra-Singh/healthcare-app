package me.shail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A CSV file loaded by an ETL run, with the checksum used to detect changes.
 */
@Entity
@Table(schema = "etl", name = "load_file")
@IdClass(LoadFileId.class)
public class LoadFile {

    @Id
    @Column(name = "run_id")
    public Long runId;

    @Id
    @Column(name = "file_name", length = 100)
    public String fileName;

    /** Hex SHA-256 of the file contents; the column is CHAR(64) */
    @Column(nullable = false, length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    public String checksum;

    @Column(name = "file_size", nullable = false)
    public Long fileSize;

    /** Rows copied into the raw table; null until the file is loaded */
    @Column(name = "row_count")
    public Integer rowCount;
}
