package me.shail.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.LanguageDto;
import me.shail.repository.LanguageRepository;

@ApplicationScoped
@WithSession(stateless = true)
public class LanguageService {

    @Inject
    LanguageRepository languageRepository;

    public Uni<LanguageDto> findById(Short id) {
        return languageRepository.findById(id).map(LanguageDto::from);
    }

    public Uni<List<LanguageDto>> findAll() {
        return languageRepository.findAllOrderedByName()
                .map(list -> list.stream().map(LanguageDto::from).toList());
    }

    /** Case-insensitive; null when there is no match. */
    public Uni<LanguageDto> findByName(String name) {
        return languageRepository.findByName(name)
                .onFailure(NoResultException.class).recoverWithNull()
                .map(LanguageDto::from);
    }
}
