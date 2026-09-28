package me.shail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * The ETL's last check of the data folder: a single row, overwritten after every check.
 */
@Entity
@Table(schema = "etl", name = "heartbeat")
public class EtlHeartbeat {

    /** Always 1 */
    public static final short ID = 1;

    @Id
    @Column(name = "heartbeat_id")
    public Short id;

    @Column(name = "checked_at", nullable = false)
    public OffsetDateTime checkedAt;

    /** waiting, unchanged, loaded, failed or busy */
    @Column(nullable = false, length = 20)
    public String outcome;

    public String detail;

    @Column(name = "interval_seconds", nullable = false)
    public Integer intervalSeconds;
}
