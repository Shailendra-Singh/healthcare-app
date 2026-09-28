package me.shail.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import me.shail.model.DiagnosisCode;
import me.shail.model.Patient;
import me.shail.model.PatientDiagnosis;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

class PatientDiagnosisDtoTest {

    @Test
    void mapsPatientIdCodeAndDate() {
        Patient patient = TestData.patient(9L, null, null);
        DiagnosisCode code = TestData.diagnosisCode(TestData.conditionGroup((short) 2));
        PatientDiagnosis diagnosis = TestData.patientDiagnosis(15L, patient, code);

        PatientDiagnosisDto dto = PatientDiagnosisDto.from(diagnosis);

        assertEquals(15L, dto.id());
        assertEquals(9L, dto.patientId());
        assertEquals(DiagnosisCodeDto.from(code), dto.diagnosisCode());
        assertEquals(diagnosis.diagnosedDate, dto.diagnosedDate());
    }

    @Test
    void nullMapsToNull() {
        assertNull(PatientDiagnosisDto.from(null));
    }
}
