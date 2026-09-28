package me.shail.repository.stateless;

import io.quarkus.data.hibernate.RecordRepository;
import io.smallrye.mutiny.Uni;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Query;
import java.util.List;
import me.shail.model.Specialty;

public interface SpecialtyQueryRepository extends RecordRepository.Reactive.CustomId<Specialty, Short> {

    @Find
    @OrderBy("name")
    Uni<List<Specialty>> findAllOrderedByName();

    /** Case-insensitive, matching how the ETL de-duplicates names. */
    @Query("where lower(name) = lower(:name)")
    Uni<Specialty> findByName(String name);
}
