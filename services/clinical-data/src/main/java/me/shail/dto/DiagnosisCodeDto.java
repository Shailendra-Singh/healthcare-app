package me.shail.dto;

import me.shail.model.DiagnosisCode;

/**
 * @param conditionGroup the ICD-10 family, or null when the code is not in a tracked family
 */
public record DiagnosisCodeDto(String icdCode, String description, ConditionGroupDto conditionGroup) {

    /** Requires {@code conditionGroup} to be fetched. */
    public static DiagnosisCodeDto from(DiagnosisCode code) {
        return code == null ? null
                : new DiagnosisCodeDto(code.icdCode, code.description, ConditionGroupDto.from(code.conditionGroup));
    }
}
