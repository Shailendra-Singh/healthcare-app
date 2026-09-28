package me.shail.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.data.page.PageRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import me.shail.dto.EvaluationInputDto;
import me.shail.model.Patient;
import me.shail.repository.EncounterRepository;
import me.shail.repository.LabResultRepository;
import me.shail.repository.PatientDiagnosisRepository;
import me.shail.repository.PatientRepository;

/**
 * Bulk input for the rules-engine: one page of patients with their diagnoses, latest labs and visits,
 * in four queries per page rather than several calls per patient.
 */
@ApplicationScoped
@WithSession(stateless = true)
public class EvaluationInputService {

    @Inject
    PatientRepository patientRepository;

    @Inject
    PatientDiagnosisRepository patientDiagnosisRepository;

    @Inject
    LabResultRepository labResultRepository;

    @Inject
    EncounterRepository encounterRepository;

    /** Patients ordered by id; {@code page} starts at 1. Visits are split into past and future at {@code asOf}. */
    public Uni<List<EvaluationInputDto>> findPage(long page, int size, LocalDate asOf) {
        return patientRepository.findPageForEvaluation(PageRequest.ofPage(page, size, false)).chain(patients -> {
            if (patients.isEmpty()) {
                return Uni.createFrom().item(List.<EvaluationInputDto>of());
            }
            List<Long> ids = patients.stream().map(p -> p.id).toList();
            // One reactive session runs one query at a time, so these run in sequence
            return patientDiagnosisRepository.findFactsByPatients(ids)
                    .chain(diagnoses -> labResultRepository.findLatestPerTestByPatients(ids)
                            .chain(labs -> encounterRepository.summarizeVisitsByPatients(ids, asOf)
                                    .map(visits -> assemble(patients, diagnoses, labs, visits))));
        });
    }

    private static List<EvaluationInputDto> assemble(List<Patient> patients, List<Object[]> diagnosisRows,
            List<Object[]> labRows, List<Object[]> visitRows) {
        Map<Long, List<EvaluationInputDto.Diagnosis>> diagnoses = group(diagnosisRows, row ->
                new EvaluationInputDto.Diagnosis((String) row[1], (String) row[2], Boolean.TRUE.equals(row[3]),
                        (LocalDate) row[4]));
        Map<Long, List<EvaluationInputDto.LabResult>> labs = group(labRows, row ->
                new EvaluationInputDto.LabResult((String) row[1], (BigDecimal) row[2], (LocalDate) row[3]));
        Map<Long, List<EvaluationInputDto.Visits>> visits = group(visitRows, row ->
                new EvaluationInputDto.Visits((String) row[1], (LocalDate) row[2], (LocalDate) row[3]));

        return patients.stream()
                .map(p -> new EvaluationInputDto(p.id, p.sourcePatientId, p.dateOfBirth, p.gender,
                        diagnoses.getOrDefault(p.id, List.of()),
                        labs.getOrDefault(p.id, List.of()),
                        visits.getOrDefault(p.id, List.of())))
                .toList();
    }

    /** Groups rows by their first column, the patient id. */
    private static <T> Map<Long, List<T>> group(List<Object[]> rows, Function<Object[], T> mapper) {
        Map<Long, List<T>> grouped = new HashMap<>();
        for (Object[] row : rows) {
            grouped.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add(mapper.apply(row));
        }
        return grouped;
    }
}
