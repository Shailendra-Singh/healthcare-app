package me.shail.repository;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.SpecialtyDto;
import me.shail.repository.stateless.SpecialtyQueryRepository;

@ApplicationScoped
@WithSession(stateless = true)
public class SpecialtyRepository {

    @Inject
    SpecialtyQueryRepository specialtyQueryRepository;

    public Uni<SpecialtyDto> findById(Short id) {
        return specialtyQueryRepository.findById(id).map(SpecialtyDto::from);
    }

    public Uni<List<SpecialtyDto>> findAll() {
        return specialtyQueryRepository.findAllOrderedByName()
                .map(list -> list.stream().map(SpecialtyDto::from).toList());
    }

    /** Case-insensitive; null when there is no match. */
    public Uni<SpecialtyDto> findByName(String name) {
        return specialtyQueryRepository.findByName(name)
                .onFailure(NoResultException.class).recoverWithNull()
                .map(SpecialtyDto::from);
    }
}
