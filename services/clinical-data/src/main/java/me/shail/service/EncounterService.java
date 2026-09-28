package me.shail.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.time.LocalDate;
import java.util.List;
import me.shail.dto.EncounterDto;
import me.shail.repository.EncounterRepository;

@ApplicationScoped
@WithSession(stateless = true)
public class EncounterService {

    @Inject
    EncounterRepository encounterRepository;

    /** Null when there is no match. */
    public Uni<EncounterDto> findById(Long id) {
        return encounterRepository.findWithDetails(id)
                .onFailure(NoResultException.class).recoverWithNull()
                .map(EncounterDto::from);
    }

    /** Past and scheduled encounters, newest first. */
    public Uni<List<EncounterDto>> findByPatient(Long patientId) {
        return encounterRepository.findByPatient(patientId)
                .map(list -> list.stream().map(EncounterDto::from).toList());
    }

    /** Scheduled appointments from today on, soonest first. */
    public Uni<List<EncounterDto>> findUpcoming(Long patientId) {
        return encounterRepository.findUpcomingByPatient(patientId, LocalDate.now())
                .map(list -> list.stream().map(EncounterDto::from).toList());
    }
}
