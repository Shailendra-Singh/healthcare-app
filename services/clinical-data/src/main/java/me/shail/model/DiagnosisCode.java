package me.shail.model;

import io.quarkus.data.hibernate.ManagedEntity;
import io.quarkus.data.hibernate.ManagedRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(schema = "dbo", name = "diagnosis_code")
public class DiagnosisCode implements ManagedEntity.Reactive.CustomId {

    /** ICD-10 code with the dot, e.g. E11.65 */
    @Id
    @Column(name = "icd_code", length = 8)
    public String icdCode;

    @Column(nullable = false, length = 255)
    public String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condition_group_id")
    public ConditionGroup conditionGroup;

    public interface Repo extends ManagedRepository.Reactive.CustomId<DiagnosisCode, String> {
    }
}
