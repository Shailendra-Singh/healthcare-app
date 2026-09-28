package me.shail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import me.shail.model.PatientProgram;
import me.shail.model.ProgramVersion;

/**
 * A program the patient is in, the tier they were placed in and why, and their care needs.
 *
 * @param tierId   null when eligible but no tier matched
 * @param evidence what matched, e.g. {"age": 67} or {"HbA1c": {"value": 9.4, "date": "2026-05-02"}}
 */
public record PatientProgramDto(
        String programId,
        String programName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String shortName,
        String tierId,
        String tierName,
        Map<String, Object> evidence,
        List<CareNeedDto> needs) {

    public static PatientProgramDto from(PatientProgram membership, ProgramVersion version, List<CareNeedDto> needs) {
        return new PatientProgramDto(membership.programId, version.name, version.shortName, membership.tierId,
                membership.tierName, membership.evidence, needs);
    }
}
