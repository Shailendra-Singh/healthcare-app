package me.shail.repository;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.LabResultDto;
import me.shail.repository.stateless.LabResultQueryRepository;

@ApplicationScoped
@WithSession(stateless = true)
public class LabResultRepository {

    @Inject
    LabResultQueryRepository labResultQueryRepository;

    /** Null when there is no match. */
    public Uni<LabResultDto> findById(Long id) {
        return labResultQueryRepository.findWithDetails(id)
                .onFailure(NoResultException.class).recoverWithNull()
                .map(LabResultDto::from);
    }

    /** Newest result first. */
    public Uni<List<LabResultDto>> findByPatient(Long patientId) {
        return labResultQueryRepository.findByPatient(patientId)
                .map(list -> list.stream().map(LabResultDto::from).toList());
    }

    /** Most recent result of a test (case-insensitive name, e.g. "HbA1c"); null when there is none. */
    public Uni<LabResultDto> findLatest(Long patientId, String testName) {
        return labResultQueryRepository.findByPatientAndTest(patientId, testName)
                .map(list -> list.isEmpty() ? null : LabResultDto.from(list.getFirst()));
    }
}
