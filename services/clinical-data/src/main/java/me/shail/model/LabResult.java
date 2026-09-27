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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Natural key: (patient, labTest, resultDate).
 */
@Entity
@Table(schema = "dbo", name = "lab_result")
public class LabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lab_result_id")
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    public Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_test_id", nullable = false)
    public LabTest labTest;

    @Column(name = "result_value", nullable = false, precision = 12, scale = 4)
    public BigDecimal resultValue;

    @Column(name = "result_date", nullable = false)
    public LocalDate resultDate;

    // Set by the database default
    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
