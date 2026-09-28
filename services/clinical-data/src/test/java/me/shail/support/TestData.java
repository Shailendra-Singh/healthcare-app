package me.shail.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Random;
import me.shail.model.ConditionGroup;
import me.shail.model.DiagnosisCode;
import me.shail.model.Encounter;
import me.shail.model.LabResult;
import me.shail.model.LabTest;
import me.shail.model.Language;
import me.shail.model.Patient;
import me.shail.model.PatientDiagnosis;
import me.shail.model.Provider;
import me.shail.model.Specialty;
import net.datafaker.Faker;

/**
 * Builds unsaved entities filled with realistic fake data, for DTO and service tests.
 * The fixed seed keeps generated values reproducible between runs.
 */
public final class TestData {

    public static final Faker FAKER = new Faker(Locale.US, new Random(20260927L));

    private TestData() {
    }

    public static Language language(short id) {
        Language language = new Language();
        language.id = id;
        language.name = FAKER.nation().language();
        return language;
    }

    public static Specialty specialty(short id) {
        Specialty specialty = new Specialty();
        specialty.id = id;
        specialty.name = FAKER.options().option("PCP", "Endocrinology", "Cardiology", "Nephrology");
        return specialty;
    }

    public static LabTest labTest(short id) {
        LabTest labTest = new LabTest();
        labTest.id = id;
        labTest.name = FAKER.options().option("HbA1c", "LDL", "eGFR", "TSH");
        return labTest;
    }

    public static Provider provider(long id) {
        Provider provider = new Provider();
        provider.id = id;
        provider.name = providerName();
        provider.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return provider;
    }

    public static ConditionGroup conditionGroup(short id) {
        ConditionGroup group = new ConditionGroup();
        group.id = id;
        group.icdPrefix = "E11";
        group.conditionName = "Type 2 Diabetes";
        group.chronic = true;
        return group;
    }

    public static DiagnosisCode diagnosisCode(ConditionGroup group) {
        DiagnosisCode code = new DiagnosisCode();
        code.icdCode = group == null ? "Z00.00" : group.icdPrefix + "." + FAKER.number().numberBetween(10, 99);
        code.description = FAKER.medical().diseaseName();
        code.conditionGroup = group;
        return code;
    }

    public static Patient patient(long id, Language language, Provider pcp) {
        Patient patient = new Patient();
        patient.id = id;
        patient.sourcePatientId = sourcePatientId();
        patient.firstName = FAKER.name().firstName();
        patient.lastName = FAKER.name().lastName();
        patient.dateOfBirth = dateOfBirth();
        patient.gender = gender();
        patient.phone = phone();
        patient.language = language;
        patient.pcpProvider = pcp;
        patient.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        patient.updatedAt = patient.createdAt;
        return patient;
    }

    public static PatientDiagnosis patientDiagnosis(long id, Patient patient, DiagnosisCode code) {
        PatientDiagnosis diagnosis = new PatientDiagnosis();
        diagnosis.id = id;
        diagnosis.patient = patient;
        diagnosis.diagnosisCode = code;
        diagnosis.diagnosedDate = pastDate();
        return diagnosis;
    }

    public static LabResult labResult(long id, Patient patient, LabTest labTest, LocalDate resultDate) {
        LabResult result = new LabResult();
        result.id = id;
        result.patient = patient;
        result.labTest = labTest;
        result.resultValue = resultValue();
        result.resultDate = resultDate;
        return result;
    }

    public static Encounter encounter(long id, Patient patient, Specialty specialty, Provider provider, LocalDate date) {
        Encounter encounter = new Encounter();
        encounter.id = id;
        encounter.patient = patient;
        encounter.specialty = specialty;
        encounter.provider = provider;
        encounter.encounterDate = date;
        return encounter;
    }

    // --- Individual values, also used to seed the database ---

    public static String sourcePatientId() {
        return "P" + FAKER.number().digits(8);
    }

    public static String providerName() {
        return "Dr. " + FAKER.name().lastName() + " " + FAKER.number().digits(4);
    }

    public static LocalDate dateOfBirth() {
        return LocalDate.now().minusYears(FAKER.number().numberBetween(18, 90)).minusDays(FAKER.number().numberBetween(0, 364));
    }

    public static char gender() {
        return FAKER.options().option('M', 'F');
    }

    public static String phone() {
        return FAKER.phoneNumber().cellPhone();
    }

    public static LocalDate pastDate() {
        return LocalDate.now().minusDays(FAKER.number().numberBetween(30, 3650));
    }

    public static BigDecimal resultValue() {
        return BigDecimal.valueOf(FAKER.number().randomDouble(2, 4, 12)).setScale(4, RoundingMode.HALF_UP);
    }
}
