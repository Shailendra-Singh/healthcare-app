package me.shail.repository;

import io.quarkus.data.hibernate.RecordRepository;
import me.shail.model.EtlHeartbeat;

/** The single heartbeat row is read with the inherited {@code findById(EtlHeartbeat.ID)}. */
public interface EtlHeartbeatRepository extends RecordRepository.Reactive.CustomId<EtlHeartbeat, Short> {
}
