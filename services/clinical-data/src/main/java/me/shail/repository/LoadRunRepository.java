package me.shail.repository;

import io.quarkus.data.hibernate.RecordRepository;
import io.smallrye.mutiny.Uni;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.Query;
import java.util.List;
import me.shail.model.LoadRun;

public interface LoadRunRepository extends RecordRepository.Reactive.CustomId<LoadRun, Long> {

    @Query("from LoadRun order by id desc limit 1")
    Uni<LoadRun> findLatest();

    @Query("from LoadRun where status = :status order by id desc limit 1")
    Uni<LoadRun> findLatestByStatus(String status);

    @Query("from LoadRun order by id desc")
    Uni<List<LoadRun>> findNewestFirst(PageRequest pageRequest);
}
