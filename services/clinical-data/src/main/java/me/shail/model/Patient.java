package me.shail.model;

import io.quarkus.data.hibernate.ManagedEntity;
import io.quarkus.data.hibernate.ManagedRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(schema = "dbo", name = "patient")
public class Patient implements ManagedEntity.Reactive.CustomId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patient_id")
    public Long id;

    /** patient_id from patients.csv */
    @Column(name = "source_patient_id", nullable = false, length = 64, unique = true)
    public String sourcePatientId;

    @Column(name = "first_name", nullable = false, length = 100)
    public String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    public String lastName;

    @Column(name = "date_of_birth", nullable = false)
    public LocalDate dateOfBirth;

    /** M or F */
    @Column(nullable = false)
    public Character gender;

    @Column(length = 32)
    public String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_id")
    public Language language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pcp_provider_id")
    public Provider pcpProvider;

    // Set by the database default
    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false)
    public OffsetDateTime updatedAt;

    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public interface Repo extends ManagedRepository.Reactive.CustomId<Patient, Long> {
    }
}
