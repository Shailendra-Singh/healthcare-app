package me.shail.repository;

import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;
import java.time.OffsetDateTime;
import java.util.Optional;
import me.shail.model.GenerationRun;

@Repository
public interface GenerationRunRepository {

    @Insert
    void insert(GenerationRun run);

    @Update
    void update(GenerationRun run);

    @Find
    Optional<GenerationRun> findById(Long id);

    @Query("from GenerationRun order by id desc limit 1")
    Optional<GenerationRun> findLatest();

    @Query("from GenerationRun where status = :status order by evaluationRunId desc, id desc limit 1")
    Optional<GenerationRun> findLatestByStatus(GenerationRun.Status status);

    @Query("select count(*) from GenerationRun where status = :status")
    long countByStatus(GenerationRun.Status status);

    @Query("update GenerationRun set status = :newStatus, finishedAt = :finishedAt, errorMessage = :errorMessage"
            + " where status = :currentStatus")
    int updateStatus(GenerationRun.Status currentStatus, GenerationRun.Status newStatus, OffsetDateTime finishedAt,
            String errorMessage);

    /** Runs left RUNNING by a process that stopped mid-run would block every later run. */
    default int failAbandoned(OffsetDateTime now) {
        return updateStatus(GenerationRun.Status.RUNNING, GenerationRun.Status.FAILED, now,
                "Abandoned: task-generation stopped before the run finished");
    }
}
