package me.shail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A care task for one patient, program, specialty and task type.
 */
@Entity
@Table(schema = "task", name = "task")
public class Task {

    public enum Status {
        OPEN, IN_PROGRESS,
        /** Closed by a person: done */
        COMPLETED,
        /** Closed by a person: not doing it */
        CANCELLED,
        /** Closed by the system: the need was met, booked or no longer applies */
        RESOLVED;

        public boolean isActive() {
            return this == OPEN || this == IN_PROGRESS;
        }
    }

    public static final String SYSTEM = "SYSTEM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    public Long id;

    /** patient_id from patients.csv; patient details come from clinical-data */
    @Column(name = "source_patient_id", nullable = false, length = 64)
    public String sourcePatientId;

    @Column(name = "program_id", nullable = false, length = 100)
    public String programId;

    @Column(name = "tier_id", nullable = false, length = 100)
    public String tierId;

    @Column(nullable = false, length = 100)
    public String specialty;

    /** A {@link TaskType} code */
    @Column(name = "task_type", nullable = false, length = 30)
    public String taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status;

    /** normal or high */
    @Column(nullable = false, length = 10)
    public String priority;

    @Column(name = "cadence_days", nullable = false)
    public Integer cadenceDays;

    @Column(name = "due_date", nullable = false)
    public LocalDate dueDate;

    /** Null for referrals */
    @Column(name = "last_visit_date")
    public LocalDate lastVisitDate;

    public String note;

    @Column(length = 100)
    public String assignee;

    @Column(name = "first_evaluation_run_id", nullable = false)
    public Long firstEvaluationRunId;

    @Column(name = "last_evaluation_run_id", nullable = false)
    public Long lastEvaluationRunId;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;

    @Column(name = "closed_at")
    public OffsetDateTime closedAt;

    /** SYSTEM or the person who closed it */
    @Column(name = "closed_by", length = 100)
    public String closedBy;

    @Column(name = "close_reason")
    public String closeReason;

    /** Optimistic locking: a person's change and a reconciliation never silently overwrite each other */
    @Version
    public Integer version;
}
