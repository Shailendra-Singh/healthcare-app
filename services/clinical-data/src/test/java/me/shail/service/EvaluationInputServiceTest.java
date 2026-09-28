package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import jakarta.data.page.PageRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import me.shail.dto.EvaluationInputDto;
import me.shail.model.Patient;
import me.shail.repository.EncounterRepository;
import me.shail.repository.LabResultRepository;
import me.shail.repository.PatientDiagnosisRepository;
import me.shail.repository.PatientRepository;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationInputServiceTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 6, 1);

    @Mock
    PatientRepository patientRepository;

    @Mock
    PatientDiagnosisRepository patientDiagnosisRepository;

    @Mock
    LabResultRepository labResultRepository;

    @Mock
    EncounterRepository encounterRepository;

    @InjectMocks
    EvaluationInputService service;

    @Test
    void assemblesEachPatientsFactsFromTheBulkQueries() {
        Patient first = TestData.patient(1L, null, null);
        Patient second = TestData.patient(2L, null, null);
        when(patientRepository.findPageForEvaluation(any())).thenReturn(Uni.createFrom().item(List.of(first, second)));
        when(patientDiagnosisRepository.findFactsByPatients(List.of(1L, 2L))).thenReturn(Uni.createFrom().item(List.<Object[]>of(
                new Object[] {1L, "E11.65", "E11", true, LocalDate.of(2020, 1, 1)},
                new Object[] {1L, "Z00.00", null, null, LocalDate.of(2021, 1, 1)})));
        when(labResultRepository.findLatestPerTestByPatients(List.of(1L, 2L))).thenReturn(Uni.createFrom().item(List.<Object[]>of(
                new Object[] {1L, "HbA1c", new BigDecimal("9.4"), LocalDate.of(2026, 5, 2)})));
        when(encounterRepository.summarizeVisitsByPatients(List.of(1L, 2L), AS_OF)).thenReturn(Uni.createFrom().item(List.<Object[]>of(
                new Object[] {2L, "PCP", LocalDate.of(2025, 1, 1), null})));

        List<EvaluationInputDto> page = service.findPage(3, 50, AS_OF).await().indefinitely();

        ArgumentCaptor<PageRequest> request = ArgumentCaptor.forClass(PageRequest.class);
        verify(patientRepository).findPageForEvaluation(request.capture());
        assertEquals(3, request.getValue().page());
        assertEquals(50, request.getValue().size());

        EvaluationInputDto p1 = page.get(0);
        assertEquals(first.sourcePatientId, p1.sourcePatientId());
        assertEquals(first.dateOfBirth, p1.dateOfBirth());
        assertEquals(List.of(
                new EvaluationInputDto.Diagnosis("E11.65", "E11", true, LocalDate.of(2020, 1, 1)),
                new EvaluationInputDto.Diagnosis("Z00.00", null, false, LocalDate.of(2021, 1, 1))), p1.diagnoses());
        assertEquals(List.of(new EvaluationInputDto.LabResult("HbA1c", new BigDecimal("9.4"), LocalDate.of(2026, 5, 2))),
                p1.latestLabResults());
        assertTrue(p1.visits().isEmpty());

        EvaluationInputDto p2 = page.get(1);
        assertTrue(p2.diagnoses().isEmpty());
        assertTrue(p2.latestLabResults().isEmpty());
        assertEquals(List.of(new EvaluationInputDto.Visits("PCP", LocalDate.of(2025, 1, 1), null)), p2.visits());
    }

    @Test
    void emptyPageSkipsTheFactQueries() {
        when(patientRepository.findPageForEvaluation(any())).thenReturn(Uni.createFrom().item(List.of()));

        assertTrue(service.findPage(99, 50, AS_OF).await().indefinitely().isEmpty());
        verifyNoInteractions(patientDiagnosisRepository, labResultRepository, encounterRepository);
    }
}
