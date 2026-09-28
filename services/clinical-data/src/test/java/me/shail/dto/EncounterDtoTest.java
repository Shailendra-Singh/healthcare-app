package me.shail.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import me.shail.model.Encounter;
import me.shail.model.Provider;
import me.shail.model.Specialty;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

class EncounterDtoTest {

    @Test
    void mapsPatientIdSpecialtyProviderAndDate() {
        Specialty specialty = TestData.specialty((short) 1);
        Provider provider = TestData.provider(4L);
        LocalDate date = LocalDate.now().plusMonths(2);
        Encounter encounter = TestData.encounter(11L, TestData.patient(5L, null, null), specialty, provider, date);

        EncounterDto dto = EncounterDto.from(encounter);

        assertEquals(11L, dto.id());
        assertEquals(5L, dto.patientId());
        assertEquals(new SpecialtyDto((short) 1, specialty.name), dto.specialty());
        assertEquals(new ProviderDto(4L, provider.name), dto.provider());
        assertEquals(date, dto.encounterDate());
    }

    @Test
    void encounterWithoutProviderHasNullProvider() {
        Encounter encounter = TestData.encounter(1L, TestData.patient(1L, null, null), TestData.specialty((short) 1),
                null, LocalDate.now());

        assertNull(EncounterDto.from(encounter).provider());
    }

    @Test
    void nullMapsToNull() {
        assertNull(EncounterDto.from(null));
    }
}
