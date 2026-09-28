package me.shail.repository;

import io.quarkus.data.hibernate.RecordRepository;
import io.smallrye.mutiny.Uni;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Query;
import java.util.List;
import me.shail.model.Language;

public interface LanguageRepository extends RecordRepository.Reactive.CustomId<Language, Short> {

    @Find
    @OrderBy("name")
    Uni<List<Language>> findAllOrderedByName();

    /** Case-insensitive, matching how the ETL de-duplicates names. */
    @Query("where lower(name) = lower(:name)")
    Uni<Language> findByName(String name);
}
