package me.shail.model;

import io.quarkus.data.hibernate.ManagedEntity;
import io.quarkus.data.hibernate.ManagedRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "dbo", name = "lab_test")
public class LabTest implements ManagedEntity.Reactive.CustomId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lab_test_id")
    public Short id;

    @Column(name = "test_name", nullable = false, length = 100)
    public String name;

    public interface Repo extends ManagedRepository.Reactive.CustomId<LabTest, Short> {
    }
}
