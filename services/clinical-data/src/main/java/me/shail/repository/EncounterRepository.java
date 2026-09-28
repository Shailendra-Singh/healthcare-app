package me.shail.repository;

import io.quarkus.data.hibernate.RecordRepository;
import io.smallrye.mutiny.Uni;
import jakarta.data.repository.Query;
import java.time.LocalDate;
import java.util.List;
import me.shail.model.Encounter;

public interface EncounterRepository extends RecordRepository.Reactive.CustomId<Encounter, Long> {

    @Query("from Encounter e join fetch e.patient join fetch e.specialty left join fetch e.provider"
            + " where e.id = :id")
    Uni<Encounter> findWithDetails(Long id);

    @Query("from Encounter e join fetch e.patient p join fetch e.specialty left join fetch e.provider"
            + " where p.id = :patientId order by e.encounterDate desc")
    Uni<List<Encounter>> findByPatient(Long patientId);

    /** Scheduled appointments: encounters on or after {@code from}. */
    @Query("from Encounter e join fetch e.patient p join fetch e.specialty left join fetch e.provider"
            + " where p.id = :patientId and e.encounterDate >= :from order by e.encounterDate")
    Uni<List<Encounter>> findUpcomingByPatient(Long patientId, LocalDate from);
}
