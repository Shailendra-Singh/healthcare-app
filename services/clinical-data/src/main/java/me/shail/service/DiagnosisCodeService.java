package me.shail.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.DiagnosisCodeDto;
import me.shail.repository.DiagnosisCodeRepository;

@ApplicationScoped
@WithSession(stateless = true)
public class DiagnosisCodeService {

    @Inject
    DiagnosisCodeRepository diagnosisCodeRepository;

    /** Null when there is no match. */
    public Uni<DiagnosisCodeDto> findById(String icdCode) {
        return diagnosisCodeRepository.findWithGroup(icdCode)
                .onFailure(NoResultException.class).recoverWithNull()
                .map(DiagnosisCodeDto::from);
    }

    public Uni<List<DiagnosisCodeDto>> findAll() {
        return diagnosisCodeRepository.findAllWithGroup()
                .map(list -> list.stream().map(DiagnosisCodeDto::from).toList());
    }

    /** Codes in a chronic condition family (E10, E11, I10, ...). */
    public Uni<List<DiagnosisCodeDto>> findChronic() {
        return diagnosisCodeRepository.findChronic()
                .map(list -> list.stream().map(DiagnosisCodeDto::from).toList());
    }
}
