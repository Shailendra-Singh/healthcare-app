package me.shail.repository;

import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import java.util.List;
import me.shail.model.PatientProgram;

@Repository
public interface PatientProgramRepository {

    @Insert
    void insertAll(List<PatientProgram> patientPrograms);

    @Query("from PatientProgram where runId = :runId and sourcePatientId = :sourcePatientId order by programId")
    List<PatientProgram> findByPatient(Long runId, String sourcePatientId);

    /** The program versions a run used. */
    @Query("select distinct programVersionId from PatientProgram where runId = :runId")
    List<Long> findProgramVersionIds(Long runId);

    /** Rows of [program id, tier id or null, patient count]. */
    @Query("select programId, tierId, count(*) from PatientProgram where runId = :runId"
            + " group by programId, tierId order by programId, tierId")
    List<Object[]> countByTier(Long runId);
}
