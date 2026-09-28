package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.LabTestDto;
import me.shail.model.LabTest;
import me.shail.repository.LabTestRepository;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabTestServiceTest {

    @Mock
    LabTestRepository labTestRepository;

    @InjectMocks
    LabTestService service;

    @Test
    void findByIdMapsToDto() {
        LabTest entity = TestData.labTest((short) 3);
        when(labTestRepository.findById((short) 3)).thenReturn(Uni.createFrom().item(entity));

        assertEquals(LabTestDto.from(entity), service.findById((short) 3).await().indefinitely());
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        when(labTestRepository.findById((short) 3)).thenReturn(Uni.createFrom().nullItem());

        assertNull(service.findById((short) 3).await().indefinitely());
    }

    @Test
    void findAllKeepsRepositoryOrder() {
        LabTest first = TestData.labTest((short) 3);
        LabTest second = TestData.labTest((short) 3);
        when(labTestRepository.findAllOrderedByName()).thenReturn(Uni.createFrom().item(List.of(first, second)));

        assertEquals(List.of(LabTestDto.from(first), LabTestDto.from(second)), service.findAll().await().indefinitely());
    }

    @Test
    void findByNameMapsToDto() {
        LabTest entity = TestData.labTest((short) 3);
        when(labTestRepository.findByName(entity.name)).thenReturn(Uni.createFrom().item(entity));

        assertEquals(LabTestDto.from(entity), service.findByName(entity.name).await().indefinitely());
    }

    @Test
    void findByNameReturnsNullWhenNoMatch() {
        when(labTestRepository.findByName("missing")).thenReturn(Uni.createFrom().failure(new NoResultException()));

        assertNull(service.findByName("missing").await().indefinitely());
    }

    @Test
    void findByNamePropagatesOtherFailures() {
        when(labTestRepository.findByName("boom")).thenReturn(Uni.createFrom().failure(new IllegalStateException("db down")));

        assertThrows(IllegalStateException.class, () -> service.findByName("boom").await().indefinitely());
    }
}
