package me.shail.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import me.shail.model.ConditionGroup;
import me.shail.model.LabTest;
import me.shail.model.Language;
import me.shail.model.Provider;
import me.shail.model.Specialty;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

class LookupDtoTest {

    @Test
    void languageCopiesIdAndName() {
        Language language = TestData.language((short) 3);
        assertEquals(new LanguageDto((short) 3, language.name), LanguageDto.from(language));
    }

    @Test
    void specialtyCopiesIdAndName() {
        Specialty specialty = TestData.specialty((short) 4);
        assertEquals(new SpecialtyDto((short) 4, specialty.name), SpecialtyDto.from(specialty));
    }

    @Test
    void labTestCopiesIdAndName() {
        LabTest labTest = TestData.labTest((short) 5);
        assertEquals(new LabTestDto((short) 5, labTest.name), LabTestDto.from(labTest));
    }

    @Test
    void providerCopiesIdAndName() {
        Provider provider = TestData.provider(6L);
        assertEquals(new ProviderDto(6L, provider.name), ProviderDto.from(provider));
    }

    @Test
    void conditionGroupCopiesAllFields() {
        ConditionGroup group = TestData.conditionGroup((short) 2);
        assertEquals(new ConditionGroupDto((short) 2, "E11", "Type 2 Diabetes", true), ConditionGroupDto.from(group));
    }

    @Test
    void nullEntitiesMapToNull() {
        assertNull(LanguageDto.from(null));
        assertNull(SpecialtyDto.from(null));
        assertNull(LabTestDto.from(null));
        assertNull(ProviderDto.from(null));
        assertNull(ConditionGroupDto.from(null));
    }
}
