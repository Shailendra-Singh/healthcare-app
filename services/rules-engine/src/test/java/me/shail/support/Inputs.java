package me.shail.support;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import me.shail.client.EvaluationInput;
import me.shail.rules.ProgramCatalog;

/** Builds clinical-data evaluation inputs, and loads the repo's real care program files. */
public final class Inputs {

    private final String sourcePatientId = "P" + Patients.FAKER.number().digits(8);
    private final LocalDate dateOfBirth;
    private final List<EvaluationInput.Diagnosis> diagnoses = new ArrayList<>();
    private final List<EvaluationInput.LabResult> labs = new ArrayList<>();
    private final List<EvaluationInput.Visits> visits = new ArrayList<>();

    private Inputs(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public static Inputs aged(int age, LocalDate asOf) {
        return new Inputs(asOf.minusYears(age).minusDays(Patients.FAKER.number().numberBetween(0, 300)));
    }

    public Inputs diagnosis(String icdCode, String conditionGroup, boolean chronic) {
        diagnoses.add(new EvaluationInput.Diagnosis(icdCode, conditionGroup, chronic, LocalDate.of(2020, 1, 1)));
        return this;
    }

    public Inputs lab(String test, String value, LocalDate date) {
        labs.add(new EvaluationInput.LabResult(test, new BigDecimal(value), date));
        return this;
    }

    public Inputs visits(String specialty, LocalDate last, LocalDate next) {
        visits.add(new EvaluationInput.Visits(specialty, last, next));
        return this;
    }

    public EvaluationInput build() {
        return new EvaluationInput(Patients.FAKER.number().randomNumber(), sourcePatientId, dateOfBirth,
                Patients.FAKER.options().option('M', 'F'), List.copyOf(diagnoses), List.copyOf(labs), List.copyOf(visits));
    }

    /** care-programs/ at the repo root, parsed the same way the catalog does. */
    public static ProgramCatalog.LoadedProgram repoProgram(String fileName) {
        try {
            return ProgramCatalog.parse(fileName, Files.readString(Path.of("../../care-programs", fileName)));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ProgramCatalog.Load repoPrograms() {
        return new ProgramCatalog.Load(
                List.of(repoProgram("diabetes-management.yaml"), repoProgram("primary-care-wellness.yaml")), List.of());
    }
}
