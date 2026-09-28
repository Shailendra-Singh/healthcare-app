package me.shail.repository;

import io.quarkus.data.hibernate.RecordRepository;
import io.smallrye.mutiny.Uni;
import jakarta.data.repository.Query;
import java.util.List;
import me.shail.model.LabResult;

public interface LabResultRepository extends RecordRepository.Reactive.CustomId<LabResult, Long> {

    @Query("from LabResult r join fetch r.patient join fetch r.labTest where r.id = :id")
    Uni<LabResult> findWithDetails(Long id);

    @Query("from LabResult r join fetch r.patient p join fetch r.labTest"
            + " where p.id = :patientId order by r.resultDate desc")
    Uni<List<LabResult>> findByPatient(Long patientId);

    /** Newest first, so the first element is the latest result. */
    @Query("from LabResult r join fetch r.patient p join fetch r.labTest t"
            + " where p.id = :patientId and lower(t.name) = lower(:testName) order by r.resultDate desc")
    Uni<List<LabResult>> findByPatientAndTest(Long patientId, String testName);

    /** Each patient's most recent result per test: rows of [patient id, test name, value, date]. */
    @Query("select r.patient.id, t.name, r.resultValue, r.resultDate from LabResult r join r.labTest t"
            + " where r.patient.id in :patientIds and r.resultDate = (select max(r2.resultDate) from LabResult r2"
            + " where r2.patient = r.patient and r2.labTest = r.labTest)")
    Uni<List<Object[]>> findLatestPerTestByPatients(List<Long> patientIds);
}
