package me.shail.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import me.shail.model.Patient;

public record PatientDto(
        Long id,
        String sourcePatientId,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        Character gender,
        String phone,
        LanguageDto language,
        ProviderDto pcpProvider,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /** Requires {@code language} and {@code pcpProvider} to be fetched. */
    public static PatientDto from(Patient patient) {
        return patient == null ? null
                : new PatientDto(
                        patient.id,
                        patient.sourcePatientId,
                        patient.firstName,
                        patient.lastName,
                        patient.dateOfBirth,
                        patient.gender,
                        patient.phone,
                        LanguageDto.from(patient.language),
                        ProviderDto.from(patient.pcpProvider),
                        patient.createdAt,
                        patient.updatedAt);
    }
}
