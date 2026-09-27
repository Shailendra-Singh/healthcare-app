package me.shail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Natural key: (patient, diagnosisCode, diagnosedDate).
 */
@Entity
@Table(schema = "dbo", name = "patient_diagnosis")
public class PatientDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patient_diagnosis_id")
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    public Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "icd_code", nullable = false)
    public DiagnosisCode diagnosisCode;

    @Column(name = "diagnosed_date", nullable = false)
    public LocalDate diagnosedDate;

    // Set by the database default
    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
