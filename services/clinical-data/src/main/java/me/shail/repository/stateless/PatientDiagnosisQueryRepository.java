package me.shail.repository.stateless;

import io.quarkus.data.hibernate.RecordRepository;
import io.smallrye.mutiny.Uni;
import jakarta.data.repository.Query;
import java.util.List;
import me.shail.model.PatientDiagnosis;

public interface PatientDiagnosisQueryRepository extends RecordRepository.Reactive.CustomId<PatientDiagnosis, Long> {

    @Query("from PatientDiagnosis pd join fetch pd.patient join fetch pd.diagnosisCode d"
            + " left join fetch d.conditionGroup where pd.id = :id")
    Uni<PatientDiagnosis> findWithDetails(Long id);

    @Query("from PatientDiagnosis pd join fetch pd.patient p join fetch pd.diagnosisCode d"
            + " left join fetch d.conditionGroup where p.id = :patientId order by pd.diagnosedDate desc")
    Uni<List<PatientDiagnosis>> findByPatient(Long patientId);
}
