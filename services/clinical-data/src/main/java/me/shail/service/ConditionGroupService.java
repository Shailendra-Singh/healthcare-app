package me.shail.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.ConditionGroupDto;
import me.shail.repository.ConditionGroupRepository;

@ApplicationScoped
@WithSession(stateless = true)
public class ConditionGroupService {

    @Inject
    ConditionGroupRepository conditionGroupRepository;

    public Uni<ConditionGroupDto> findById(Short id) {
        return conditionGroupRepository.findById(id).map(ConditionGroupDto::from);
    }

    public Uni<List<ConditionGroupDto>> findAll() {
        return conditionGroupRepository.findAllOrderedByPrefix()
                .map(list -> list.stream().map(ConditionGroupDto::from).toList());
    }

    /** Null when there is no match. */
    public Uni<ConditionGroupDto> findByIcdPrefix(String icdPrefix) {
        return conditionGroupRepository.findByIcdPrefix(icdPrefix)
                .onFailure(NoResultException.class).recoverWithNull()
                .map(ConditionGroupDto::from);
    }
}
