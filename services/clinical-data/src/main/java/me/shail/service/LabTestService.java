package me.shail.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.LabTestDto;
import me.shail.repository.LabTestRepository;

@ApplicationScoped
@WithSession(stateless = true)
public class LabTestService {

    @Inject
    LabTestRepository labTestRepository;

    public Uni<LabTestDto> findById(Short id) {
        return labTestRepository.findById(id).map(LabTestDto::from);
    }

    public Uni<List<LabTestDto>> findAll() {
        return labTestRepository.findAllOrderedByName()
                .map(list -> list.stream().map(LabTestDto::from).toList());
    }

    /** Case-insensitive; null when there is no match. */
    public Uni<LabTestDto> findByName(String name) {
        return labTestRepository.findByName(name)
                .onFailure(NoResultException.class).recoverWithNull()
                .map(LabTestDto::from);
    }
}
