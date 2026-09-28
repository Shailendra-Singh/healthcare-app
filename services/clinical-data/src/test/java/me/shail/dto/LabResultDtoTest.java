package me.shail.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import me.shail.model.LabResult;
import me.shail.model.LabTest;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

class LabResultDtoTest {

    @Test
    void mapsPatientIdTestValueAndDate() {
        LabTest labTest = TestData.labTest((short) 2);
        LabResult result = TestData.labResult(30L, TestData.patient(8L, null, null), labTest, TestData.pastDate());

        LabResultDto dto = LabResultDto.from(result);

        assertEquals(30L, dto.id());
        assertEquals(8L, dto.patientId());
        assertEquals(new LabTestDto((short) 2, labTest.name), dto.labTest());
        assertEquals(result.resultValue, dto.resultValue());
        assertEquals(result.resultDate, dto.resultDate());
    }

    @Test
    void nullMapsToNull() {
        assertNull(LabResultDto.from(null));
    }
}
