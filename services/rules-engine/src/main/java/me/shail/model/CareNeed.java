package me.shail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import me.shail.rules.ProgramEvaluator.NeedStatus;

/**
 * A recurring visit a patient needs under a program's tier, with where they stand on it.
 */
@Entity
@Table(schema = "eval", name = "care_need")
@IdClass(CareNeedId.class)
public class CareNeed {

    @Id
    @Column(name = "run_id")
    public Long runId;

    @Id
    @Column(name = "source_patient_id", length = 64)
    public String sourcePatientId;

    @Id
    @Column(name = "program_id", length = 100)
    public String programId;

    @Id
    @Column(length = 100)
    public String specialty;

    @Column(name = "tier_id", nullable = false, length = 100)
    public String tierId;

    @Column(name = "every_days", nullable = false)
    public Integer everyDays;

    /** Null when never seen */
    @Column(name = "last_visit_date")
    public LocalDate lastVisitDate;

    @Column(name = "due_date", nullable = false)
    public LocalDate dueDate;

    @Column(name = "next_scheduled_date")
    public LocalDate nextScheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public NeedStatus status;

    /** normal or high */
    @Column(nullable = false, length = 10)
    public String priority;

    public String note;
}
