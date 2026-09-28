package me.shail.repository;

import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import java.util.List;
import java.util.Optional;
import me.shail.model.ProgramVersion;

@Repository
public interface ProgramVersionRepository {

    @Insert
    void insert(ProgramVersion version);

    @Find
    Optional<ProgramVersion> find(String programId, String checksum);

    @Find
    Optional<ProgramVersion> findById(Long id);

    /** The most recent version recorded for a file: its last good version when it now fails to load. */
    @Query("from ProgramVersion where sourceFile = :sourceFile order by id desc limit 1")
    Optional<ProgramVersion> findLatestBySourceFile(String sourceFile);

    @Query("from ProgramVersion where id in :ids")
    List<ProgramVersion> findByIds(List<Long> ids);
}
