package me.shail.repository;

import io.quarkus.data.hibernate.RecordRepository;
import io.smallrye.mutiny.Uni;
import jakarta.data.repository.Query;
import me.shail.model.LoadRun;

public interface LoadRunRepository extends RecordRepository.Reactive.CustomId<LoadRun, Long> {

    @Query("from LoadRun order by id desc limit 1")
    Uni<LoadRun> findLatest();

    @Query("from LoadRun where status = :status order by id desc limit 1")
    Uni<LoadRun> findLatestByStatus(String status);
}
