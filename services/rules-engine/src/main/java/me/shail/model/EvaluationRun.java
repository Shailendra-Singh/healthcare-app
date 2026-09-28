package me.shail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One evaluation of every patient against every care program.
 */
@Entity
@Table(schema = "eval", name = "evaluation_run")
public class EvaluationRun {

    public enum Trigger { SCHEDULED, STARTUP, MANUAL }

    public enum Status { RUNNING, SUCCEEDED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    public Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Trigger trigger;

    /** "Today" for ages, lab windows and due dates */
    @Column(name = "as_of_date", nullable = false)
    public LocalDate asOfDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status;

    /** clinical-data's latest ETL run when this run started; null if it has none */
    @Column(name = "source_etl_run_id")
    public Long sourceEtlRunId;

    @Column(name = "patients_evaluated")
    public Integer patientsEvaluated;

    /** Program files that failed to load, one per line */
    @Column(name = "program_errors")
    public String programErrors;

    @Column(name = "error_message")
    public String errorMessage;

    @Column(name = "started_at", nullable = false)
    public OffsetDateTime startedAt;

    @Column(name = "finished_at")
    public OffsetDateTime finishedAt;
}
