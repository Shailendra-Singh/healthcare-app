package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import jakarta.persistence.NoResultException;
import java.time.LocalDate;
import java.util.List;
import me.shail.dto.EncounterDto;
import me.shail.model.Encounter;
import me.shail.model.Patient;
import me.shail.repository.EncounterRepository;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EncounterServiceTest {

    @Mock
    EncounterRepository encounterRepository;

    @InjectMocks
    EncounterService service;

    private final Patient patient = TestData.patient(5L, null, null);

    private Encounter encounter(long id, LocalDate date) {
        return TestData.encounter(id, patient, TestData.specialty((short) 1), TestData.provider(3L), date);
    }

    @Test
    void findByIdMapsToDto() {
        Encounter encounter = encounter(1L, TestData.pastDate());
        when(encounterRepository.findWithDetails(1L)).thenReturn(Uni.createFrom().item(encounter));

        assertEquals(EncounterDto.from(encounter), service.findById(1L).await().indefinitely());
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        when(encounterRepository.findWithDetails(9L)).thenReturn(Uni.createFrom().failure(new NoResultException()));

        assertNull(service.findById(9L).await().indefinitely());
    }

    @Test
    void findByPatientMapsEveryEncounter() {
        Encounter encounter = encounter(1L, TestData.pastDate());
        when(encounterRepository.findByPatient(5L)).thenReturn(Uni.createFrom().item(List.of(encounter)));

        assertEquals(List.of(EncounterDto.from(encounter)), service.findByPatient(5L).await().indefinitely());
    }

    @Test
    void findUpcomingAsksForEncountersFromToday() {
        Encounter scheduled = encounter(2L, LocalDate.now().plusMonths(1));
        when(encounterRepository.findUpcomingByPatient(eq(5L), any())).thenReturn(Uni.createFrom().item(List.of(scheduled)));

        List<EncounterDto> upcoming = service.findUpcoming(5L).await().indefinitely();

        verify(encounterRepository).findUpcomingByPatient(5L, LocalDate.now());
        assertEquals(List.of(EncounterDto.from(scheduled)), upcoming);
    }
}
