package me.shail.dto;

import java.time.LocalDate;
import me.shail.model.Encounter;

/**
 * @param provider null when the encounter has no provider
 */
public record EncounterDto(Long id, Long patientId, SpecialtyDto specialty, ProviderDto provider, LocalDate encounterDate) {

    /** Requires {@code patient}, {@code specialty} and {@code provider} to be fetched. */
    public static EncounterDto from(Encounter encounter) {
        return encounter == null ? null
                : new EncounterDto(
                        encounter.id,
                        encounter.patient.id,
                        SpecialtyDto.from(encounter.specialty),
                        ProviderDto.from(encounter.provider),
                        encounter.encounterDate);
    }
}
