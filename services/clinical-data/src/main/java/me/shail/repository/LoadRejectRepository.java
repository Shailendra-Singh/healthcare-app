package me.shail.repository;

import io.quarkus.data.hibernate.RecordRepository;
import io.smallrye.mutiny.Uni;
import jakarta.data.repository.Query;
import java.util.List;
import me.shail.model.LoadReject;

public interface LoadRejectRepository extends RecordRepository.Reactive.CustomId<LoadReject, Long> {

    /** Rows of [file_name, rejected row count] for one run. */
    @Query("select fileName, count(*) from LoadReject where runId = :runId group by fileName")
    Uni<List<Object[]>> countByFile(Long runId);
}
