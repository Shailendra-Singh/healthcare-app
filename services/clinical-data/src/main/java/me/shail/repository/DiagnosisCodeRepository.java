package me.shail.repository;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.DiagnosisCodeDto;
import me.shail.repository.stateless.DiagnosisCodeQueryRepository;

@ApplicationScoped
@WithSession(stateless = true)
public class DiagnosisCodeRepository {

    @Inject
    DiagnosisCodeQueryRepository diagnosisCodeQueryRepository;

    /** Null when there is no match. */
    public Uni<DiagnosisCodeDto> findById(String icdCode) {
        return diagnosisCodeQueryRepository.findWithGroup(icdCode)
                .onFailure(NoResultException.class).recoverWithNull()
                .map(DiagnosisCodeDto::from);
    }

    public Uni<List<DiagnosisCodeDto>> findAll() {
        return diagnosisCodeQueryRepository.findAllWithGroup()
                .map(list -> list.stream().map(DiagnosisCodeDto::from).toList());
    }

    /** Codes in a chronic condition family (E10, E11, I10, ...). */
    public Uni<List<DiagnosisCodeDto>> findChronic() {
        return diagnosisCodeQueryRepository.findChronic()
                .map(list -> list.stream().map(DiagnosisCodeDto::from).toList());
    }
}
