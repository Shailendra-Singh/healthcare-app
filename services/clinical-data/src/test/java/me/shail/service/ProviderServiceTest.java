package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.ProviderDto;
import me.shail.model.Provider;
import me.shail.repository.ProviderRepository;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

    @Mock
    ProviderRepository providerRepository;

    @InjectMocks
    ProviderService service;

    @Test
    void findByIdMapsToDto() {
        Provider entity = TestData.provider(3L);
        when(providerRepository.findById(3L)).thenReturn(Uni.createFrom().item(entity));

        assertEquals(ProviderDto.from(entity), service.findById(3L).await().indefinitely());
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        when(providerRepository.findById(3L)).thenReturn(Uni.createFrom().nullItem());

        assertNull(service.findById(3L).await().indefinitely());
    }

    @Test
    void findAllKeepsRepositoryOrder() {
        Provider first = TestData.provider(3L);
        Provider second = TestData.provider(3L);
        when(providerRepository.findAllOrderedByName()).thenReturn(Uni.createFrom().item(List.of(first, second)));

        assertEquals(List.of(ProviderDto.from(first), ProviderDto.from(second)), service.findAll().await().indefinitely());
    }

    @Test
    void findByNameMapsToDto() {
        Provider entity = TestData.provider(3L);
        when(providerRepository.findByName(entity.name)).thenReturn(Uni.createFrom().item(entity));

        assertEquals(ProviderDto.from(entity), service.findByName(entity.name).await().indefinitely());
    }

    @Test
    void findByNameReturnsNullWhenNoMatch() {
        when(providerRepository.findByName("missing")).thenReturn(Uni.createFrom().failure(new NoResultException()));

        assertNull(service.findByName("missing").await().indefinitely());
    }

    @Test
    void findByNamePropagatesOtherFailures() {
        when(providerRepository.findByName("boom")).thenReturn(Uni.createFrom().failure(new IllegalStateException("db down")));

        assertThrows(IllegalStateException.class, () -> service.findByName("boom").await().indefinitely());
    }
}
