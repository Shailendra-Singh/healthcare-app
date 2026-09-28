package me.shail.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import me.shail.rules.PatientFacts;
import net.datafaker.Faker;

/** Builds {@link PatientFacts} for rule tests; a fake patient id and name-free facts by default. */
public final class Patients {

    public static final Faker FAKER = new Faker(Locale.US, new Random(20260928L));

    private final String sourcePatientId = "P" + FAKER.number().digits(8);
    private LocalDate dateOfBirth;
    private final List<PatientFacts.Diagnosis> diagnoses = new ArrayList<>();
    private final Map<String, PatientFacts.LabResult> labs = new HashMap<>();
    private final Map<String, PatientFacts.Visits> visits = new HashMap<>();

    private Patients() {
    }

    /** A patient who turns {@code age} years old on {@code asOf}, or was born earlier in that year of age. */
    public static Patients aged(int age, LocalDate asOf) {
        Patients patient = new Patients();
        patient.dateOfBirth = asOf.minusYears(age).minusDays(FAKER.number().numberBetween(0, 300));
        return patient;
    }

    public static Patients bornOn(LocalDate dateOfBirth) {
        Patients patient = new Patients();
        patient.dateOfBirth = dateOfBirth;
        return patient;
    }

    public Patients diagnosis(String icdCode, String conditionGroup, boolean chronic) {
        diagnoses.add(new PatientFacts.Diagnosis(icdCode, conditionGroup, chronic));
        return this;
    }

    public Patients lab(String test, String value, LocalDate date) {
        labs.put(PatientFacts.key(test), new PatientFacts.LabResult(test, new BigDecimal(value), date));
        return this;
    }

    public Patients visits(String specialty, LocalDate last, LocalDate next) {
        visits.put(PatientFacts.key(specialty), new PatientFacts.Visits(last, next));
        return this;
    }

    public PatientFacts build() {
        return new PatientFacts(sourcePatientId, dateOfBirth, List.copyOf(diagnoses), Map.copyOf(labs), Map.copyOf(visits));
    }
}
