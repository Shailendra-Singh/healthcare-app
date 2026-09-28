package me.shail.repository;

import jakarta.data.page.PageRequest;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import me.shail.model.EvaluationRun;

@Repository
public interface EvaluationRunRepository {

    @Insert
    void insert(EvaluationRun run);

    @Update
    void update(EvaluationRun run);

    @Find
    Optional<EvaluationRun> findById(Long id);

    @Query("from EvaluationRun order by id desc limit 1")
    Optional<EvaluationRun> findLatest();

    @Query("from EvaluationRun order by id desc")
    List<EvaluationRun> findNewestFirst(PageRequest pageRequest);

    @Query("from EvaluationRun where status = :status order by id desc limit 1")
    Optional<EvaluationRun> findLatestByStatus(EvaluationRun.Status status);

    @Query("from EvaluationRun where id = :id and status = :status")
    Optional<EvaluationRun> findByIdAndStatus(Long id, EvaluationRun.Status status);

    default Optional<EvaluationRun> findLatestSucceeded() {
        return findLatestByStatus(EvaluationRun.Status.SUCCEEDED);
    }

    @Query("select count(*) from EvaluationRun where status = :status")
    long countByStatus(EvaluationRun.Status status);

    default long countRunning() {
        return countByStatus(EvaluationRun.Status.RUNNING);
    }

    @Query("update EvaluationRun set status = :newStatus, finishedAt = :finishedAt, errorMessage = :errorMessage"
            + " where status = :currentStatus")
    int updateStatus(EvaluationRun.Status currentStatus, EvaluationRun.Status newStatus, OffsetDateTime finishedAt,
            String errorMessage);

    /** Runs left RUNNING by a process that stopped mid-run would block every later run. */
    default int failAbandoned(OffsetDateTime now) {
        return updateStatus(EvaluationRun.Status.RUNNING, EvaluationRun.Status.FAILED, now,
                "Abandoned: the rules-engine stopped before the run finished");
    }

    /** Deletes runs older than the newest {@code keep}; their results go with them (ON DELETE CASCADE). */
    @Query("delete from EvaluationRun where id < (select min(r.id) from (select r2.id as id from EvaluationRun r2"
            + " order by r2.id desc limit :keep) r)")
    int deleteAllButNewest(int keep);
}
