package me.shail.repository;

import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.shail.model.Task;
import org.hibernate.StatelessSession;

@Repository
public interface TaskRepository {

    List<Task.Status> ACTIVE = List.of(Task.Status.OPEN, Task.Status.IN_PROGRESS);
    List<Task.Status> CLOSED_BY_PEOPLE = List.of(Task.Status.COMPLETED, Task.Status.CANCELLED);

    StatelessSession session();

    @Insert
    void insert(Task task);

    @Update
    void update(Task task);

    @Find
    Optional<Task> findById(Long id);

    @Query("from Task where sourcePatientId in :patientIds and status in :statuses")
    List<Task> findByPatientsAndStatuses(List<String> patientIds, List<Task.Status> statuses);

    /** Active tasks that the given evaluation run (and every later one) no longer called for. */
    @Query("from Task where status in :statuses and lastEvaluationRunId < :evaluationRunId order by id")
    List<Task> findByStatusesNotSeenSince(List<Task.Status> statuses, Long evaluationRunId);

    @Query("from Task where sourcePatientId = :sourcePatientId order by createdAt desc, id desc")
    List<Task> findByPatient(String sourcePatientId);

    @Query("select count(*) from Task where sourcePatientId = :sourcePatientId and programId = :programId"
            + " and specialty = :specialty and taskType = :taskType and status in :statuses and lastVisitDate = :lastVisitDate")
    long countWithLastVisit(String sourcePatientId, String programId, String specialty, String taskType,
            List<Task.Status> statuses, LocalDate lastVisitDate);

    @Query("select count(*) from Task where sourcePatientId = :sourcePatientId and programId = :programId"
            + " and specialty = :specialty and taskType = :taskType and status in :statuses and lastVisitDate is null")
    long countNeverSeen(String sourcePatientId, String programId, String specialty, String taskType,
            List<Task.Status> statuses);

    /**
     * Whether a person already closed (completed or cancelled) a task for this same gap, i.e. the same need with
     * the same last visit. Their decision stands until the data changes.
     */
    default boolean closedByPersonForSameGap(String sourcePatientId, String programId, String specialty, String taskType,
            LocalDate lastVisitDate) {
        return (lastVisitDate == null
                ? countNeverSeen(sourcePatientId, programId, specialty, taskType, CLOSED_BY_PEOPLE)
                : countWithLastVisit(sourcePatientId, programId, specialty, taskType, CLOSED_BY_PEOPLE, lastVisitDate)) > 0;
    }

    /** Tasks matching whichever optional filters are given, most urgent first; {@code page} starts at 1. */
    default List<Task> search(List<Task.Status> statuses, String taskType, String programId, String specialty,
            String assignee, int page, int size) {
        StringBuilder hql = new StringBuilder("from Task where status in :statuses");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("statuses", statuses);
        List<String> clauses = new ArrayList<>();
        if (taskType != null) {
            clauses.add("taskType = :taskType");
            params.put("taskType", taskType);
        }
        if (programId != null) {
            clauses.add("programId = :programId");
            params.put("programId", programId);
        }
        if (specialty != null) {
            clauses.add("lower(specialty) = lower(:specialty)");
            params.put("specialty", specialty);
        }
        if (assignee != null) {
            clauses.add("assignee = :assignee");
            params.put("assignee", assignee);
        }
        clauses.forEach(clause -> hql.append(" and ").append(clause));
        // 'high' sorts before 'normal'
        hql.append(" order by dueDate, priority, id");

        var query = session().createSelectionQuery(hql.toString(), Task.class);
        params.forEach(query::setParameter);
        return query.setFirstResult((page - 1) * size).setMaxResults(size).getResultList();
    }
}
