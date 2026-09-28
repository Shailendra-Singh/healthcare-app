package me.shail.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import me.shail.model.ConditionGroup;
import me.shail.model.DiagnosisCode;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

class DiagnosisCodeDtoTest {

    @Test
    void mapsCodeWithItsConditionGroup() {
        ConditionGroup group = TestData.conditionGroup((short) 2);
        DiagnosisCode code = TestData.diagnosisCode(group);

        DiagnosisCodeDto dto = DiagnosisCodeDto.from(code);

        assertEquals(code.icdCode, dto.icdCode());
        assertEquals(code.description, dto.description());
        assertEquals(ConditionGroupDto.from(group), dto.conditionGroup());
    }

    @Test
    void codeOutsideATrackedFamilyHasNoConditionGroup() {
        DiagnosisCodeDto dto = DiagnosisCodeDto.from(TestData.diagnosisCode(null));

        assertNull(dto.conditionGroup());
    }

    @Test
    void nullMapsToNull() {
        assertNull(DiagnosisCodeDto.from(null));
    }
}
