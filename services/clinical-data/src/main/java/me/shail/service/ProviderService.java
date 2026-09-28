package me.shail.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.ProviderDto;
import me.shail.repository.ProviderRepository;

@ApplicationScoped
@WithSession(stateless = true)
public class ProviderService {

    @Inject
    ProviderRepository providerRepository;

    public Uni<ProviderDto> findById(Long id) {
        return providerRepository.findById(id).map(ProviderDto::from);
    }

    public Uni<List<ProviderDto>> findAll() {
        return providerRepository.findAllOrderedByName()
                .map(list -> list.stream().map(ProviderDto::from).toList());
    }

    /** Case-insensitive; null when there is no match. */
    public Uni<ProviderDto> findByName(String name) {
        return providerRepository.findByName(name)
                .onFailure(NoResultException.class).recoverWithNull()
                .map(ProviderDto::from);
    }
}
