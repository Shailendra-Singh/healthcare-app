package me.shail.repository;

import io.quarkus.data.hibernate.RecordRepository;
import io.smallrye.mutiny.Uni;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.Query;
import java.util.List;
import me.shail.model.Patient;

public interface PatientRepository extends RecordRepository.Reactive.CustomId<Patient, Long> {

    @Query("from Patient p left join fetch p.language left join fetch p.pcpProvider where p.id = :id")
    Uni<Patient> findWithDetails(Long id);

    @Query("from Patient p left join fetch p.language left join fetch p.pcpProvider"
            + " where p.sourcePatientId = :sourcePatientId")
    Uni<Patient> findBySourcePatientId(String sourcePatientId);

    @Query("from Patient p left join fetch p.language left join fetch p.pcpProvider order by p.id")
    Uni<List<Patient>> findPage(PageRequest pageRequest);
}
