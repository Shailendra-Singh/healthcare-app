package me.shail.repository;

import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.shail.model.CareNeed;
import me.shail.rules.ProgramEvaluator.NeedStatus;
import org.hibernate.StatelessSession;

@Repository
public interface CareNeedRepository {

    StatelessSession session();

    @Insert
    void insertAll(List<CareNeed> careNeeds);

    @Query("from CareNeed where runId = :runId and sourcePatientId = :sourcePatientId order by programId, specialty")
    List<CareNeed> findByPatient(Long runId, String sourcePatientId);

    /** Every care need of a run in a stable order, for paging through all of them; {@code page} starts at 1. */
    @Query("from CareNeed where runId = :runId order by sourcePatientId, programId, specialty")
    List<CareNeed> findPageByRun(Long runId, jakarta.data.page.PageRequest pageRequest);

    /** Rows of [status, count]. */
    @Query("select status, count(*) from CareNeed where runId = :runId group by status")
    List<Object[]> countByStatus(Long runId);

    /**
     * Care needs of one run, most urgent first (earliest due date), filtered by whichever of the optional
     * criteria are given. {@code page} starts at 1.
     */
    default List<CareNeed> search(Long runId, String programId, String tierId, NeedStatus status, String specialty,
            int page, int size) {
        StringBuilder hql = new StringBuilder("from CareNeed where runId = :runId");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("runId", runId);
        List<String> clauses = new ArrayList<>();
        if (programId != null) {
            clauses.add("programId = :programId");
            params.put("programId", programId);
        }
        if (tierId != null) {
            clauses.add("tierId = :tierId");
            params.put("tierId", tierId);
        }
        if (status != null) {
            clauses.add("status = :status");
            params.put("status", status);
        }
        if (specialty != null) {
            clauses.add("lower(specialty) = lower(:specialty)");
            params.put("specialty", specialty);
        }
        clauses.forEach(clause -> hql.append(" and ").append(clause));
        hql.append(" order by dueDate, sourcePatientId, programId, specialty");

        var query = session().createSelectionQuery(hql.toString(), CareNeed.class);
        params.forEach(query::setParameter);
        return query.setFirstResult((page - 1) * size).setMaxResults(size).getResultList();
    }
}
