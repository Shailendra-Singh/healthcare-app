package me.shail.repository.stateless;

import io.quarkus.data.hibernate.RecordRepository;
import io.smallrye.mutiny.Uni;
import jakarta.data.repository.Query;
import java.util.List;
import me.shail.model.LabResult;

public interface LabResultQueryRepository extends RecordRepository.Reactive.CustomId<LabResult, Long> {

    @Query("from LabResult r join fetch r.patient join fetch r.labTest where r.id = :id")
    Uni<LabResult> findWithDetails(Long id);

    @Query("from LabResult r join fetch r.patient p join fetch r.labTest"
            + " where p.id = :patientId order by r.resultDate desc")
    Uni<List<LabResult>> findByPatient(Long patientId);

    /** Newest first, so the first element is the latest result. */
    @Query("from LabResult r join fetch r.patient p join fetch r.labTest t"
            + " where p.id = :patientId and lower(t.name) = lower(:testName) order by r.resultDate desc")
    Uni<List<LabResult>> findByPatientAndTest(Long patientId, String testName);
}
