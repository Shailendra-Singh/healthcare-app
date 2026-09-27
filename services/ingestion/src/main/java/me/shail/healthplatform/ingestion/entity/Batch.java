package me.shail.healthplatform.ingestion.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import me.shail.healthplatform.ingestion.model.BatchStatus;

import java.time.Instant;
import java.util.UUID;

/** One poll that found at least one new file. */
@Entity
@Table(name = "batch")
public class Batch extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Public ID used in events and the API. */
    @Column(name = "batch_id")
    public UUID batchId;

    @Enumerated(EnumType.STRING)
    public BatchStatus status;

    @Column(name = "received_at")
    public Instant receivedAt;

    @Column(name = "completed_at")
    public Instant completedAt;
}
