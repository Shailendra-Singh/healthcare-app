package me.shail.model;

import io.quarkus.data.hibernate.ManagedEntity;
import io.quarkus.data.hibernate.ManagedRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ICD-10 code family, e.g. E11 (Type 2 Diabetes) or G47.3 (Sleep Apnea). Seeded by V4.
 */
@Entity
@Table(schema = "dbo", name = "condition_group")
public class ConditionGroup implements ManagedEntity.Reactive.CustomId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "condition_group_id")
    public Short id;

    @Column(name = "icd_prefix", nullable = false, length = 8, unique = true)
    public String icdPrefix;

    @Column(name = "condition_name", nullable = false, length = 100)
    public String conditionName;

    @Column(name = "is_chronic", nullable = false)
    public boolean chronic;

    public interface Repo extends ManagedRepository.Reactive.CustomId<ConditionGroup, Short> {
    }
}
