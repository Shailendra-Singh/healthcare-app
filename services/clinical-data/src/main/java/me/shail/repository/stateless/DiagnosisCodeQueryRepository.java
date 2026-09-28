package me.shail.repository.stateless;

import io.quarkus.data.hibernate.RecordRepository;
import io.smallrye.mutiny.Uni;
import jakarta.data.repository.Query;
import java.util.List;
import me.shail.model.DiagnosisCode;

public interface DiagnosisCodeQueryRepository extends RecordRepository.Reactive.CustomId<DiagnosisCode, String> {

    @Query("from DiagnosisCode d left join fetch d.conditionGroup where d.icdCode = :icdCode")
    Uni<DiagnosisCode> findWithGroup(String icdCode);

    @Query("from DiagnosisCode d left join fetch d.conditionGroup order by d.icdCode")
    Uni<List<DiagnosisCode>> findAllWithGroup();

    @Query("from DiagnosisCode d join fetch d.conditionGroup g where g.chronic = true order by d.icdCode")
    Uni<List<DiagnosisCode>> findChronic();
}
