package me.shail.repository;

import io.quarkus.data.hibernate.RecordRepository;
import io.smallrye.mutiny.Uni;
import jakarta.data.repository.Query;
import java.util.List;
import me.shail.model.LoadFile;
import me.shail.model.LoadFileId;

public interface LoadFileRepository extends RecordRepository.Reactive.CustomId<LoadFile, LoadFileId> {

    @Query("from LoadFile where runId = :runId order by fileName")
    Uni<List<LoadFile>> findByRun(Long runId);
}
