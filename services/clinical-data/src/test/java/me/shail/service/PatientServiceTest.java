package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import jakarta.data.page.PageRequest;
import jakarta.persistence.NoResultException;
import java.util.List;
import me.shail.dto.PatientDto;
import me.shail.model.Patient;
import me.shail.repository.PatientRepository;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    PatientRepository patientRepository;

    @InjectMocks
    PatientService service;

    @Test
    void findByIdUsesTheQueryThatFetchesLookups() {
        Patient patient = TestData.patient(1L, TestData.language((short) 1), TestData.provider(2L));
        when(patientRepository.findWithDetails(1L)).thenReturn(Uni.createFrom().item(patient));

        assertEquals(PatientDto.from(patient), service.findById(1L).await().indefinitely());
    }

    @Test
    void findByIdReturnsNullWhenMissing() {
        when(patientRepository.findWithDetails(99L)).thenReturn(Uni.createFrom().failure(new NoResultException()));

        assertNull(service.findById(99L).await().indefinitely());
    }

    @Test
    void findByIdPropagatesOtherFailures() {
        when(patientRepository.findWithDetails(1L)).thenReturn(Uni.createFrom().failure(new IllegalStateException()));

        assertThrows(IllegalStateException.class, () -> service.findById(1L).await().indefinitely());
    }

    @Test
    void findBySourcePatientIdMapsToDto() {
        Patient patient = TestData.patient(1L, null, null);
        when(patientRepository.findBySourcePatientId(patient.sourcePatientId)).thenReturn(Uni.createFrom().item(patient));

        assertEquals(PatientDto.from(patient), service.findBySourcePatientId(patient.sourcePatientId).await().indefinitely());
    }

    @Test
    void findBySourcePatientIdReturnsNullWhenMissing() {
        when(patientRepository.findBySourcePatientId("P0")).thenReturn(Uni.createFrom().failure(new NoResultException()));

        assertNull(service.findBySourcePatientId("P0").await().indefinitely());
    }

    @Test
    void findPagePassesPageAndSizeAsAJakartaDataPageRequest() {
        Patient first = TestData.patient(21L, null, null);
        Patient second = TestData.patient(22L, null, null);
        when(patientRepository.findPage(any())).thenReturn(Uni.createFrom().item(List.of(first, second)));

        List<PatientDto> page = service.findPage(3, 10).await().indefinitely();

        ArgumentCaptor<PageRequest> request = ArgumentCaptor.forClass(PageRequest.class);
        verify(patientRepository).findPage(request.capture());
        assertEquals(3, request.getValue().page());
        assertEquals(10, request.getValue().size());
        assertEquals(List.of(PatientDto.from(first), PatientDto.from(second)), page);
    }
}
