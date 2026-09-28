package me.shail.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.data.page.PageRequest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.time.LocalDate;
import me.shail.support.ReactiveSessions;
import me.shail.support.TestData;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PatientRepositoryTest {

    @Inject
    PatientRepository repository;

    @Inject
    ReactiveSessions sessions;

    @Inject
    TestDatabase db;

    String sourcePatientId;
    String firstName;
    String languageName;
    String providerName;
    long patientId;

    @BeforeEach
    void seed() {
        db.reset();
        languageName = TestData.FAKER.nation().language();
        providerName = TestData.providerName();
        short languageId = db.insertLanguage(languageName);
        long providerId = db.insertProvider(providerName);
        sourcePatientId = TestData.sourcePatientId();
        firstName = TestData.FAKER.name().firstName();
        patientId = db.insertPatient(sourcePatientId, firstName, TestData.FAKER.name().lastName(),
                LocalDate.of(1970, 3, 12), 'F', TestData.phone(), languageId, providerId);
        db.insertPatient();
        db.insertPatient();
    }

    @Test
    @RunOnVertxContext
    void findWithDetailsFetchesLanguageAndPcp(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findWithDetails(patientId)), patient -> {
            assertEquals(sourcePatientId, patient.sourcePatientId);
            assertEquals(firstName, patient.firstName);
            assertEquals(LocalDate.of(1970, 3, 12), patient.dateOfBirth);
            assertEquals('F', patient.gender);
            assertEquals(languageName, patient.language.name);
            assertEquals(providerName, patient.pcpProvider.name);
        });
    }

    @Test
    @RunOnVertxContext
    void findWithDetailsFailsWhenMissing(UniAsserter asserter) {
        asserter.assertFailedWith(() -> sessions.read(() -> repository.findWithDetails(-1L)),
                e -> assertInstanceOf(NoResultException.class, e));
    }

    @Test
    @RunOnVertxContext
    void findBySourcePatientId(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findBySourcePatientId(sourcePatientId)),
                patient -> assertEquals(patientId, patient.id));
    }

    @Test
    @RunOnVertxContext
    void findPageIsOneBasedAndOrderedById(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findPage(PageRequest.ofPage(1, 2, false))),
                page -> assertEquals(java.util.List.of(1L, 2L), page.stream().map(p -> p.id).toList()));
        asserter.assertThat(() -> sessions.read(() -> repository.findPage(PageRequest.ofPage(2, 2, false))),
                page -> assertEquals(java.util.List.of(3L), page.stream().map(p -> p.id).toList()));
    }

    @Test
    @RunOnVertxContext
    void patientWithoutLanguageOrPcpHasNullAssociations(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findWithDetails(2L)), patient -> {
            assertNull(patient.language);
            assertNull(patient.pcpProvider);
        });
    }
}
