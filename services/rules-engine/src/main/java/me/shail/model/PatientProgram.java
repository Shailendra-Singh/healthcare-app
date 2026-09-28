package me.shail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A patient's membership in a care program in one run, and the tier they were placed in.
 */
@Entity
@Table(schema = "eval", name = "patient_program")
@IdClass(PatientProgramId.class)
public class PatientProgram {

    @Id
    @Column(name = "run_id")
    public Long runId;

    /** patient_id from patients.csv, stable across clinical-data reloads */
    @Id
    @Column(name = "source_patient_id", length = 64)
    public String sourcePatientId;

    @Id
    @Column(name = "program_id", length = 100)
    public String programId;

    @Column(name = "program_version_id", nullable = false)
    public Long programVersionId;

    /** Null when the patient is eligible but no tier matched */
    @Column(name = "tier_id", length = 100)
    public String tierId;

    @Column(name = "tier_name", length = 200)
    public String tierName;

    /** What matched, e.g. {"age": 67} or {"HbA1c": {"value": 9.4, "date": "2026-05-02"}} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    public Map<String, Object> evidence;
}
