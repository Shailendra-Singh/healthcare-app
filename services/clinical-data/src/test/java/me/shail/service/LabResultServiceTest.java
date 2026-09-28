package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import jakarta.persistence.NoResultException;
import java.time.LocalDate;
import java.util.List;
import me.shail.dto.LabResultDto;
import me.shail.model.LabResult;
import me.shail.model.LabTest;
import me.shail.model.Patient;
import me.shail.repository.LabResultRepository;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabResultServiceTest {

    @Mock
    LabResultRepository labResultRepository;

    @InjectMocks
    LabResultService service;

    private final Patient patient = TestData.patient(5L, null, null);
    private final LabTest hba1c = TestData.labTest((short) 1);

    @Test
    void findByIdMapsToDto() {
        LabResult result = TestData.labResult(1L, patient, hba1c, TestData.pastDate());
        when(labResultRepository.findWithDetails(1L)).thenReturn(Uni.createFrom().item(result));

        assertEquals(LabResultDto.from(result), service.findById(1L).await().indefinitely());
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        when(labResultRepository.findWithDetails(9L)).thenReturn(Uni.createFrom().failure(new NoResultException()));

        assertNull(service.findById(9L).await().indefinitely());
    }

    @Test
    void findByPatientMapsEveryResult() {
        LabResult result = TestData.labResult(1L, patient, hba1c, TestData.pastDate());
        when(labResultRepository.findByPatient(5L)).thenReturn(Uni.createFrom().item(List.of(result)));

        assertEquals(List.of(LabResultDto.from(result)), service.findByPatient(5L).await().indefinitely());
    }

    @Test
    void findLatestReturnsTheFirstResultOfTheNewestFirstList() {
        LabResult newest = TestData.labResult(2L, patient, hba1c, LocalDate.now().minusDays(10));
        LabResult older = TestData.labResult(1L, patient, hba1c, LocalDate.now().minusDays(400));
        when(labResultRepository.findByPatientAndTest(5L, "HbA1c")).thenReturn(Uni.createFrom().item(List.of(newest, older)));

        assertEquals(LabResultDto.from(newest), service.findLatest(5L, "HbA1c").await().indefinitely());
    }

    @Test
    void findLatestReturnsNullWhenThePatientHasNoSuchResult() {
        when(labResultRepository.findByPatientAndTest(5L, "LDL")).thenReturn(Uni.createFrom().item(List.of()));

        assertNull(service.findLatest(5L, "LDL").await().indefinitely());
    }
}
