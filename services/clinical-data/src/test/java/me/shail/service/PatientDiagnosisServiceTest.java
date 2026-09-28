package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.PatientDiagnosisDto;
import me.shail.model.Patient;
import me.shail.model.PatientDiagnosis;
import me.shail.repository.PatientDiagnosisRepository;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientDiagnosisServiceTest {

    @Mock
    PatientDiagnosisRepository patientDiagnosisRepository;

    @InjectMocks
    PatientDiagnosisService service;

    private final Patient patient = TestData.patient(5L, null, null);

    private PatientDiagnosis diagnosis(long id) {
        return TestData.patientDiagnosis(id, patient, TestData.diagnosisCode(TestData.conditionGroup((short) 2)));
    }

    @Test
    void findByIdMapsToDto() {
        PatientDiagnosis diagnosis = diagnosis(1L);
        when(patientDiagnosisRepository.findWithDetails(1L)).thenReturn(Uni.createFrom().item(diagnosis));

        assertEquals(PatientDiagnosisDto.from(diagnosis), service.findById(1L).await().indefinitely());
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        when(patientDiagnosisRepository.findWithDetails(9L)).thenReturn(Uni.createFrom().failure(new NoResultException()));

        assertNull(service.findById(9L).await().indefinitely());
    }

    @Test
    void findByPatientKeepsRepositoryOrder() {
        PatientDiagnosis newer = diagnosis(2L);
        PatientDiagnosis older = diagnosis(1L);
        when(patientDiagnosisRepository.findByPatient(5L)).thenReturn(Uni.createFrom().item(List.of(newer, older)));

        assertEquals(List.of(PatientDiagnosisDto.from(newer), PatientDiagnosisDto.from(older)),
                service.findByPatient(5L).await().indefinitely());
    }
}
