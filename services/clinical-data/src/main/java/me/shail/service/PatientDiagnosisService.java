package me.shail.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.PatientDiagnosisDto;
import me.shail.repository.PatientDiagnosisRepository;

@ApplicationScoped
@WithSession(stateless = true)
public class PatientDiagnosisService {

    @Inject
    PatientDiagnosisRepository patientDiagnosisRepository;

    /** Null when there is no match. */
    public Uni<PatientDiagnosisDto> findById(Long id) {
        return patientDiagnosisRepository.findWithDetails(id)
                .onFailure(NoResultException.class).recoverWithNull()
                .map(PatientDiagnosisDto::from);
    }

    /** Newest diagnosis first. */
    public Uni<List<PatientDiagnosisDto>> findByPatient(Long patientId) {
        return patientDiagnosisRepository.findByPatient(patientId)
                .map(list -> list.stream().map(PatientDiagnosisDto::from).toList());
    }
}
