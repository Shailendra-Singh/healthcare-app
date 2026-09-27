package me.shail.model;

import io.quarkus.data.hibernate.ManagedEntity;
import io.quarkus.data.hibernate.ManagedRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(schema = "dbo", name = "provider")
public class Provider implements ManagedEntity.Reactive.CustomId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "provider_id")
    public Long id;

    @Column(name = "provider_name", nullable = false, length = 200)
    public String name;

    // Set by the database default
    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;

    public interface Repo extends ManagedRepository.Reactive.CustomId<Provider, Long> {
    }
}
