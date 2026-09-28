package me.shail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A version of a care program file that produced results.
 */
@Entity
@Table(schema = "eval", name = "program_version")
public class ProgramVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "program_version_id")
    public Long id;

    @Column(name = "program_id", nullable = false, length = 100)
    public String programId;

    @Column(nullable = false, length = 200)
    public String name;

    @Column(name = "short_name", length = 50)
    public String shortName;

    @Column(name = "source_file", nullable = false)
    public String sourceFile;

    /** Hex SHA-256 of the file; the column is CHAR(64) */
    @Column(nullable = false, length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    public String checksum;

    /** The YAML as loaded */
    @Column(nullable = false)
    public String definition;

    /** Task for a need seen before but past its cadence: scheduling, referral, none; null without a policy */
    @Column(name = "past_cadence_task", length = 30)
    public String pastCadenceTask;

    /** Task for a need never seen: scheduling, referral, none; null without a policy */
    @Column(name = "never_seen_task", length = 30)
    public String neverSeenTask;

    @Column(name = "loaded_at", insertable = false, updatable = false)
    public OffsetDateTime loadedAt;
}
