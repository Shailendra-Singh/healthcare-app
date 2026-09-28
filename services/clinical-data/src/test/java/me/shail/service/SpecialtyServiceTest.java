package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.SpecialtyDto;
import me.shail.model.Specialty;
import me.shail.repository.SpecialtyRepository;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpecialtyServiceTest {

    @Mock
    SpecialtyRepository specialtyRepository;

    @InjectMocks
    SpecialtyService service;

    @Test
    void findByIdMapsToDto() {
        Specialty entity = TestData.specialty((short) 3);
        when(specialtyRepository.findById((short) 3)).thenReturn(Uni.createFrom().item(entity));

        assertEquals(SpecialtyDto.from(entity), service.findById((short) 3).await().indefinitely());
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        when(specialtyRepository.findById((short) 3)).thenReturn(Uni.createFrom().nullItem());

        assertNull(service.findById((short) 3).await().indefinitely());
    }

    @Test
    void findAllKeepsRepositoryOrder() {
        Specialty first = TestData.specialty((short) 3);
        Specialty second = TestData.specialty((short) 3);
        when(specialtyRepository.findAllOrderedByName()).thenReturn(Uni.createFrom().item(List.of(first, second)));

        assertEquals(List.of(SpecialtyDto.from(first), SpecialtyDto.from(second)), service.findAll().await().indefinitely());
    }

    @Test
    void findByNameMapsToDto() {
        Specialty entity = TestData.specialty((short) 3);
        when(specialtyRepository.findByName(entity.name)).thenReturn(Uni.createFrom().item(entity));

        assertEquals(SpecialtyDto.from(entity), service.findByName(entity.name).await().indefinitely());
    }

    @Test
    void findByNameReturnsNullWhenNoMatch() {
        when(specialtyRepository.findByName("missing")).thenReturn(Uni.createFrom().failure(new NoResultException()));

        assertNull(service.findByName("missing").await().indefinitely());
    }

    @Test
    void findByNamePropagatesOtherFailures() {
        when(specialtyRepository.findByName("boom")).thenReturn(Uni.createFrom().failure(new IllegalStateException("db down")));

        assertThrows(IllegalStateException.class, () -> service.findByName("boom").await().indefinitely());
    }
}
