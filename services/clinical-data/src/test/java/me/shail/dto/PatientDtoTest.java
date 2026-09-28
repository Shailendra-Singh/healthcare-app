package me.shail.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import me.shail.model.Language;
import me.shail.model.Patient;
import me.shail.model.Provider;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

class PatientDtoTest {

    @Test
    void mapsDemographicsAndNestedLookups() {
        Language language = TestData.language((short) 1);
        Provider pcp = TestData.provider(7L);
        Patient patient = TestData.patient(42L, language, pcp);

        PatientDto dto = PatientDto.from(patient);

        assertEquals(42L, dto.id());
        assertEquals(patient.sourcePatientId, dto.sourcePatientId());
        assertEquals(patient.firstName, dto.firstName());
        assertEquals(patient.lastName, dto.lastName());
        assertEquals(patient.dateOfBirth, dto.dateOfBirth());
        assertEquals(patient.gender, dto.gender());
        assertEquals(patient.phone, dto.phone());
        assertEquals(new LanguageDto((short) 1, language.name), dto.language());
        assertEquals(new ProviderDto(7L, pcp.name), dto.pcpProvider());
        assertEquals(patient.createdAt, dto.createdAt());
        assertEquals(patient.updatedAt, dto.updatedAt());
    }

    @Test
    void patientWithoutLanguageOrPcpHasNullNestedDtos() {
        PatientDto dto = PatientDto.from(TestData.patient(1L, null, null));

        assertNull(dto.language());
        assertNull(dto.pcpProvider());
    }

    @Test
    void nullMapsToNull() {
        assertNull(PatientDto.from(null));
    }
}
