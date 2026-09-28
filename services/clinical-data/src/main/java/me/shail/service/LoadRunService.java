package me.shail.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import me.shail.dto.LoadFileDto;
import me.shail.dto.LoadRunDto;
import me.shail.model.LoadRun;
import me.shail.repository.LoadFileRepository;
import me.shail.repository.LoadRejectRepository;
import me.shail.repository.LoadRunRepository;

@ApplicationScoped
@WithSession(stateless = true)
public class LoadRunService {

    @Inject
    LoadRunRepository loadRunRepository;

    @Inject
    LoadFileRepository loadFileRepository;

    @Inject
    LoadRejectRepository loadRejectRepository;

    /** The most recent run with its files and reject counts; null when the ETL has never run. */
    public Uni<LoadRunDto> findLatest() {
        return loadRunRepository.findLatest()
                .onFailure(NoResultException.class).recoverWithNull()
                .chain(run -> run == null ? Uni.createFrom().nullItem() : withFiles(run));
    }

    private Uni<LoadRunDto> withFiles(LoadRun run) {
        return loadFileRepository.findByRun(run.id)
                .chain(files -> loadRejectRepository.countByFile(run.id).map(counts -> {
                    Map<String, Long> rejectsByFile = counts.stream()
                            .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
                    List<LoadFileDto> fileDtos = files.stream()
                            .map(file -> LoadFileDto.from(file, rejectsByFile.getOrDefault(file.fileName, 0L)))
                            .toList();
                    return LoadRunDto.from(run, fileDtos);
                }));
    }
}
