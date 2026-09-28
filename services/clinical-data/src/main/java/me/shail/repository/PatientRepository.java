package me.shail.repository;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.data.page.PageRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.PatientDto;
import me.shail.repository.stateless.PatientQueryRepository;

@ApplicationScoped
@WithSession(stateless = true)
public class PatientRepository {

    @Inject
    PatientQueryRepository patientQueryRepository;

    /** Null when there is no match. */
    public Uni<PatientDto> findById(Long patientId) {
        return patientQueryRepository.findWithDetails(patientId)
                .onFailure(NoResultException.class).recoverWithNull()
                .map(PatientDto::from);
    }

    /** Looks up by patient_id from patients.csv; null when there is no match. */
    public Uni<PatientDto> findBySourcePatientId(String sourcePatientId) {
        return patientQueryRepository.findBySourcePatientId(sourcePatientId)
                .onFailure(NoResultException.class).recoverWithNull()
                .map(PatientDto::from);
    }

    /** Patients ordered by id; {@code page} starts at 1. */
    public Uni<List<PatientDto>> findPage(long page, int size) {
        return patientQueryRepository.findPage(PageRequest.ofPage(page, size, false))
                .map(list -> list.stream().map(PatientDto::from).toList());
    }
}
