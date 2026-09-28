package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.ConditionGroupDto;
import me.shail.model.ConditionGroup;
import me.shail.repository.ConditionGroupRepository;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConditionGroupServiceTest {

    @Mock
    ConditionGroupRepository conditionGroupRepository;

    @InjectMocks
    ConditionGroupService service;

    @Test
    void findByIdMapsToDto() {
        ConditionGroup group = TestData.conditionGroup((short) 2);
        when(conditionGroupRepository.findById((short) 2)).thenReturn(Uni.createFrom().item(group));

        assertEquals(ConditionGroupDto.from(group), service.findById((short) 2).await().indefinitely());
    }

    @Test
    void findAllMapsEveryGroup() {
        ConditionGroup group = TestData.conditionGroup((short) 2);
        when(conditionGroupRepository.findAllOrderedByPrefix()).thenReturn(Uni.createFrom().item(List.of(group)));

        assertEquals(List.of(ConditionGroupDto.from(group)), service.findAll().await().indefinitely());
    }

    @Test
    void findByIcdPrefixMapsToDto() {
        ConditionGroup group = TestData.conditionGroup((short) 2);
        when(conditionGroupRepository.findByIcdPrefix("E11")).thenReturn(Uni.createFrom().item(group));

        assertEquals(ConditionGroupDto.from(group), service.findByIcdPrefix("E11").await().indefinitely());
    }

    @Test
    void findByIcdPrefixReturnsNullWhenNoMatch() {
        when(conditionGroupRepository.findByIcdPrefix("X99")).thenReturn(Uni.createFrom().failure(new NoResultException()));

        assertNull(service.findByIcdPrefix("X99").await().indefinitely());
    }
}
