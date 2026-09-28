package me.shail.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import me.shail.model.LabResult;

public record LabResultDto(Long id, Long patientId, LabTestDto labTest, BigDecimal resultValue, LocalDate resultDate) {

    /** Requires {@code patient} and {@code labTest} to be fetched. */
    public static LabResultDto from(LabResult result) {
        return result == null ? null
                : new LabResultDto(
                        result.id,
                        result.patient.id,
                        LabTestDto.from(result.labTest),
                        result.resultValue,
                        result.resultDate);
    }
}
