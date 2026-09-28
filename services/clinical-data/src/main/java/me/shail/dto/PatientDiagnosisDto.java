package me.shail.dto;

import java.time.LocalDate;
import me.shail.model.PatientDiagnosis;

public record PatientDiagnosisDto(Long id, Long patientId, DiagnosisCodeDto diagnosisCode, LocalDate diagnosedDate) {

    /** Requires {@code patient}, {@code diagnosisCode} and its {@code conditionGroup} to be fetched. */
    public static PatientDiagnosisDto from(PatientDiagnosis diagnosis) {
        return diagnosis == null ? null
                : new PatientDiagnosisDto(
                        diagnosis.id,
                        diagnosis.patient.id,
                        DiagnosisCodeDto.from(diagnosis.diagnosisCode),
                        diagnosis.diagnosedDate);
    }
}
