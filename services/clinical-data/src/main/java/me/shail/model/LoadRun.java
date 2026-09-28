package me.shail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * One ETL run over the four CSV files. Written by the ETL, read-only here.
 */
@Entity
@Table(schema = "etl", name = "load_run")
public class LoadRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    public Long id;

    /** STARTED, LOADED, SUCCEEDED, FAILED or SKIPPED */
    @Column(nullable = false, length = 20)
    public String status;

    @Column(name = "error_message")
    public String errorMessage;

    @Column(name = "started_at", nullable = false)
    public OffsetDateTime startedAt;

    @Column(name = "finished_at")
    public OffsetDateTime finishedAt;
}
