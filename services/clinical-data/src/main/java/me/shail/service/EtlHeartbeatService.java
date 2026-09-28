package me.shail.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import me.shail.dto.EtlHeartbeatDto;
import me.shail.model.EtlHeartbeat;
import me.shail.repository.EtlHeartbeatRepository;

@ApplicationScoped
@WithSession(stateless = true)
public class EtlHeartbeatService {

    @Inject
    EtlHeartbeatRepository etlHeartbeatRepository;

    /** Replaced in unit tests to check staleness at a fixed time. */
    Clock clock = Clock.systemUTC();

    /** The ETL's last check; null when it has never checked. */
    public Uni<EtlHeartbeatDto> find() {
        return etlHeartbeatRepository.findById(EtlHeartbeat.ID)
                .map(heartbeat -> EtlHeartbeatDto.from(heartbeat, clock.instant()));
    }
}
