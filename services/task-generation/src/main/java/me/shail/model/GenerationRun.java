package me.shail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** One reconciliation of the tasks against a rules-engine evaluation run. */
@Entity
@Table(schema = "task", name = "generation_run")
public class GenerationRun {

    public enum Trigger { SCHEDULED, MANUAL }

    public enum Status { RUNNING, SUCCEEDED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    public Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Trigger trigger;

    @Column(name = "evaluation_run_id", nullable = false)
    public Long evaluationRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status;

    @Column(name = "needs_read")
    public Integer needsRead;

    @Column(name = "tasks_created")
    public Integer tasksCreated;

    @Column(name = "tasks_updated")
    public Integer tasksUpdated;

    @Column(name = "tasks_closed")
    public Integer tasksClosed;

    @Column(name = "error_message")
    public String errorMessage;

    @Column(name = "started_at", nullable = false)
    public OffsetDateTime startedAt;

    @Column(name = "finished_at")
    public OffsetDateTime finishedAt;
}
