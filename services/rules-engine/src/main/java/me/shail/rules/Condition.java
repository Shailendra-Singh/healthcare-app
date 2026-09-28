package me.shail.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A rule condition from a care program YAML file. See care-programs/README.md for the YAML syntax.
 */
public sealed interface Condition {

    /**
     * True when the patient matches on {@code asOf}. On a match, adds what made it match to
     * {@code evidence} (e.g. {@code age: 67}), which is stored with the result to explain it.
     */
    boolean matches(PatientFacts patient, LocalDate asOf, Map<String, Object> evidence);

    /** Every condition must match. */
    record All(List<Condition> conditions) implements Condition {
        @Override
        public boolean matches(PatientFacts patient, LocalDate asOf, Map<String, Object> evidence) {
            Map<String, Object> collected = new LinkedHashMap<>();
            for (Condition condition : conditions) {
                if (!condition.matches(patient, asOf, collected)) {
                    return false;
                }
            }
            evidence.putAll(collected);
            return true;
        }
    }

    /** At least one condition must match; evidence comes from the first that does. */
    record Any(List<Condition> conditions) implements Condition {
        @Override
        public boolean matches(PatientFacts patient, LocalDate asOf, Map<String, Object> evidence) {
            for (Condition condition : conditions) {
                Map<String, Object> collected = new LinkedHashMap<>();
                if (condition.matches(patient, asOf, collected)) {
                    evidence.putAll(collected);
                    return true;
                }
            }
            return false;
        }
    }

    record Not(Condition condition) implements Condition {
        @Override
        public boolean matches(PatientFacts patient, LocalDate asOf, Map<String, Object> evidence) {
            return !condition.matches(patient, asOf, new LinkedHashMap<>());
        }
    }

    /** Age in whole years on {@code asOf}; both bounds inclusive and optional. */
    record Age(Integer min, Integer max) implements Condition {
        @Override
        public boolean matches(PatientFacts patient, LocalDate asOf, Map<String, Object> evidence) {
            if (patient.dateOfBirth() == null) {
                return false;
            }
            int age = Period.between(patient.dateOfBirth(), asOf).getYears();
            boolean matches = (min == null || age >= min) && (max == null || age <= max);
            if (matches) {
                evidence.put("age", age);
            }
            return matches;
        }
    }

    /**
     * Any diagnosis that satisfies every given field: in a chronic (or non-chronic) condition group, in one
     * of the listed condition groups, and/or with an ICD code starting with one of the listed prefixes.
     */
    record Diagnosis(Boolean chronic, List<String> conditionGroups, List<String> icdPrefixes) implements Condition {
        @Override
        public boolean matches(PatientFacts patient, LocalDate asOf, Map<String, Object> evidence) {
            List<String> codes = patient.diagnoses().stream()
                    .filter(this::matches)
                    .map(PatientFacts.Diagnosis::icdCode)
                    .distinct()
                    .sorted()
                    .toList();
            if (codes.isEmpty()) {
                return false;
            }
            evidence.put("diagnoses", codes);
            return true;
        }

        private boolean matches(PatientFacts.Diagnosis diagnosis) {
            if (chronic != null && diagnosis.chronic() != chronic) {
                return false;
            }
            if (conditionGroups != null && (diagnosis.conditionGroup() == null
                    || conditionGroups.stream().noneMatch(g -> g.equalsIgnoreCase(diagnosis.conditionGroup())))) {
                return false;
            }
            return icdPrefixes == null || icdPrefixes.stream().anyMatch(prefix ->
                    diagnosis.icdCode().toUpperCase(Locale.ROOT).startsWith(prefix.toUpperCase(Locale.ROOT)));
        }
    }

    /**
     * The patient's most recent result of {@code test}, counted only when it falls in the last
     * {@code withinMonths} calendar months (any date when null). {@code exists} checks whether there is
     * one; {@code latest} checks its value.
     */
    record Lab(String test, Integer withinMonths, Range latest, Boolean exists) implements Condition {
        @Override
        public boolean matches(PatientFacts patient, LocalDate asOf, Map<String, Object> evidence) {
            PatientFacts.LabResult result = patient.latestLab(test);
            boolean inWindow = result != null && !result.date().isAfter(asOf)
                    && (withinMonths == null || !result.date().isBefore(asOf.minusMonths(withinMonths)));

            if (exists != null && inWindow != exists) {
                return false;
            }
            if (latest != null && (!inWindow || !latest.contains(result.value()))) {
                return false;
            }
            evidence.put(test, inWindow
                    ? Map.of("value", result.value(), "date", result.date().toString())
                    : "no result" + (withinMonths == null ? "" : " in the last " + withinMonths + " months"));
            return true;
        }
    }

    /** Matches everyone: the last tier's catch-all. */
    record Otherwise() implements Condition {
        @Override
        public boolean matches(PatientFacts patient, LocalDate asOf, Map<String, Object> evidence) {
            return true;
        }
    }

    /** Numeric bounds; at least one is set. */
    record Range(BigDecimal gt, BigDecimal gte, BigDecimal lt, BigDecimal lte) {

        public boolean contains(BigDecimal value) {
            return (gt == null || value.compareTo(gt) > 0)
                    && (gte == null || value.compareTo(gte) >= 0)
                    && (lt == null || value.compareTo(lt) < 0)
                    && (lte == null || value.compareTo(lte) <= 0);
        }
    }
}
