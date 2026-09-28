package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.LanguageDto;
import me.shail.model.Language;
import me.shail.repository.LanguageRepository;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LanguageServiceTest {

    @Mock
    LanguageRepository languageRepository;

    @InjectMocks
    LanguageService service;

    @Test
    void findByIdMapsToDto() {
        Language entity = TestData.language((short) 3);
        when(languageRepository.findById((short) 3)).thenReturn(Uni.createFrom().item(entity));

        assertEquals(LanguageDto.from(entity), service.findById((short) 3).await().indefinitely());
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        when(languageRepository.findById((short) 3)).thenReturn(Uni.createFrom().nullItem());

        assertNull(service.findById((short) 3).await().indefinitely());
    }

    @Test
    void findAllKeepsRepositoryOrder() {
        Language first = TestData.language((short) 3);
        Language second = TestData.language((short) 3);
        when(languageRepository.findAllOrderedByName()).thenReturn(Uni.createFrom().item(List.of(first, second)));

        assertEquals(List.of(LanguageDto.from(first), LanguageDto.from(second)), service.findAll().await().indefinitely());
    }

    @Test
    void findByNameMapsToDto() {
        Language entity = TestData.language((short) 3);
        when(languageRepository.findByName(entity.name)).thenReturn(Uni.createFrom().item(entity));

        assertEquals(LanguageDto.from(entity), service.findByName(entity.name).await().indefinitely());
    }

    @Test
    void findByNameReturnsNullWhenNoMatch() {
        when(languageRepository.findByName("missing")).thenReturn(Uni.createFrom().failure(new NoResultException()));

        assertNull(service.findByName("missing").await().indefinitely());
    }

    @Test
    void findByNamePropagatesOtherFailures() {
        when(languageRepository.findByName("boom")).thenReturn(Uni.createFrom().failure(new IllegalStateException("db down")));

        assertThrows(IllegalStateException.class, () -> service.findByName("boom").await().indefinitely());
    }
}
