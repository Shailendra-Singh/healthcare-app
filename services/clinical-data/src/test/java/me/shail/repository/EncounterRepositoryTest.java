package me.shail.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.time.LocalDate;
import java.util.List;
import me.shail.support.ReactiveSessions;
import me.shail.support.TestData;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EncounterRepositoryTest {

    static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

    @Inject
    EncounterRepository repository;

    @Inject
    ReactiveSessions sessions;

    @Inject
    TestDatabase db;

    long patientId;
    long pastId;
    long todayId;
    long futureId;
    String providerName;

    @BeforeEach
    void seed() {
        db.reset();
        short pcp = db.insertSpecialty("PCP");
        short cardiology = db.insertSpecialty("Cardiology");
        providerName = TestData.providerName();
        long provider = db.insertProvider(providerName);
        patientId = db.insertPatient();
        long otherPatientId = db.insertPatient();
        pastId = db.insertEncounter(patientId, pcp, provider, TODAY.minusMonths(6));
        futureId = db.insertEncounter(patientId, cardiology, null, TODAY.plusMonths(2));
        todayId = db.insertEncounter(patientId, pcp, provider, TODAY);
        db.insertEncounter(otherPatientId, pcp, provider, TODAY.plusDays(1));
    }

    @Test
    @RunOnVertxContext
    void findWithDetailsFetchesPatientSpecialtyAndProvider(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findWithDetails(pastId)), encounter -> {
            assertEquals(patientId, encounter.patient.id);
            assertEquals("PCP", encounter.specialty.name);
            assertEquals(providerName, encounter.provider.name);
        });
    }

    @Test
    @RunOnVertxContext
    void findWithDetailsReturnsEncountersWithoutAProvider(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findWithDetails(futureId)),
                encounter -> assertNull(encounter.provider));
    }

    @Test
    @RunOnVertxContext
    void findWithDetailsFailsWithNoResultWhenMissing(UniAsserter asserter) {
        asserter.assertFailedWith(() -> sessions.read(() -> repository.findWithDetails(-1L)),
                e -> assertInstanceOf(NoResultException.class, e));
    }

    @Test
    @RunOnVertxContext
    void findByPatientReturnsOnlyThatPatientNewestFirst(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findByPatient(patientId)),
                encounters -> assertEquals(List.of(futureId, todayId, pastId), encounters.stream().map(e -> e.id).toList()));
    }

    @Test
    @RunOnVertxContext
    void findUpcomingIncludesTheStartDateAndSortsSoonestFirst(UniAsserter asserter) {
        asserter.assertThat(() -> sessions.read(() -> repository.findUpcomingByPatient(patientId, TODAY)),
                encounters -> assertEquals(List.of(todayId, futureId), encounters.stream().map(e -> e.id).toList()));
    }
}
