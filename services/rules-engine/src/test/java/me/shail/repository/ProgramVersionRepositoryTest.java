package me.shail.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import me.shail.model.ProgramVersion;
import me.shail.support.Patients;
import me.shail.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProgramVersionRepositoryTest {

    @Inject
    ProgramVersionRepository repository;

    @Inject
    TestDatabase db;

    @BeforeEach
    void reset() {
        db.reset();
    }

    @Test
    void findsAVersionByProgramAndChecksum() {
        ProgramVersion v1 = insert("diabetes-management", "diabetes-management.yaml");

        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(v1.id, repository.find("diabetes-management", v1.checksum).orElseThrow().id);
            assertTrue(repository.find("diabetes-management", Patients.FAKER.hashing().sha256()).isEmpty());
        });
    }

    @Test
    void latestBySourceFileIsTheMostRecentVersionOfThatFile() {
        insert("diabetes-management", "diabetes-management.yaml");
        ProgramVersion v2 = insert("diabetes-management", "diabetes-management.yaml");
        insert("primary-care-wellness", "primary-care-wellness.yaml");

        QuarkusTransaction.requiringNew().run(() ->
                assertEquals(v2.id, repository.findLatestBySourceFile("diabetes-management.yaml").orElseThrow().id));
    }

    private ProgramVersion insert(String programId, String sourceFile) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ProgramVersion version = new ProgramVersion();
            version.programId = programId;
            version.name = Patients.FAKER.medical().diseaseName();
            version.sourceFile = sourceFile;
            version.checksum = Patients.FAKER.hashing().sha256();
            version.definition = "id: " + programId;
            repository.insert(version);
            return version;
        });
    }
}
