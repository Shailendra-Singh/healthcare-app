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

    /**
     * Per patient and specialty: rows of [patient id, specialty, last visit on or before {@code asOf},
     * next appointment after {@code asOf}]. Either date is null when there is none.
     */
    @Query("select e.patient.id, s.name,"
            + " max(e.encounterDate) filter (where e.encounterDate <= :asOf),"
            + " min(e.encounterDate) filter (where e.encounterDate > :asOf)"
            + " from Encounter e join e.specialty s where e.patient.id in :patientIds"
            + " group by e.patient.id, s.name")
    Uni<List<Object[]>> summarizeVisitsByPatients(List<Long> patientIds, LocalDate asOf);
}
