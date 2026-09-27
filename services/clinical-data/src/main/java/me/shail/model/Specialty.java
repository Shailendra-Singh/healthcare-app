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
@Table(schema = "dbo", name = "specialty")
public class Specialty implements ManagedEntity.Reactive.CustomId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "specialty_id")
    public Short id;

    @Column(name = "specialty_name", nullable = false, length = 100)
    public String name;

    public interface Repo extends ManagedRepository.Reactive.CustomId<Specialty, Short> {
    }
}
