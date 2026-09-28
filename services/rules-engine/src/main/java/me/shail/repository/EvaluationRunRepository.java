package me.shail.repository;

import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;
import java.time.OffsetDateTime;
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

    @Query("from EvaluationRun where status = SUCCEEDED order by id desc limit 1")
    Optional<EvaluationRun> findLatestSucceeded();

    @Query("select count(*) from EvaluationRun where status = RUNNING")
    long countRunning();

    /** Runs left RUNNING by a process that stopped mid-run would block every later run. */
    @Query("update EvaluationRun set status = FAILED, finishedAt = :now,"
            + " errorMessage = 'Abandoned: the rules-engine stopped before the run finished' where status = RUNNING")
    int failAbandoned(OffsetDateTime now);

    /** Deletes runs older than the newest {@code keep}; their results go with them (ON DELETE CASCADE). */
    @Query("delete from EvaluationRun where id < (select min(r.id) from (select r2.id as id from EvaluationRun r2"
            + " order by r2.id desc limit :keep) r)")
    int deleteAllButNewest(int keep);
}
