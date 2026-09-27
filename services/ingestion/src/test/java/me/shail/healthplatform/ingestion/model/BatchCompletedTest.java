package me.shail.healthplatform.ingestion.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.shail.healthplatform.ingestion.batch.CreatedBatch;
import me.shail.healthplatform.ingestion.batch.StoredFile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contract test: the batch.completed event must serialize exactly as the design doc defines it.
 */
class BatchCompletedTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesToContractJson() throws JsonProcessingException {
        UUID batchId = UUID.fromString("7f3c2a9e-1b4d-4c8e-9a51-2d6e0f7b8c34");
        CreatedBatch createdBatch = new CreatedBatch(batchId, List.of(
                new StoredFile(FileType.LABS, batchId + "/labs.csv",
                        "6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b"),
                new StoredFile(FileType.DIAGNOSES, batchId + "/diagnoses.csv",
                        "d4735e3a265e16eee03f59718b9b5d03019c07d8b6c51f90da3a666eec13ab35")));

        String actual = mapper.writeValueAsString(BatchCompleted.from(createdBatch));

        // Checksums are deliberately not part of the contract; any extra field fails the comparison.
        String expected = """
                {
                  "batchId": "%1$s",
                  "files": [
                    { "type": "LABS",      "path": "%1$s/labs.csv" },
                    { "type": "DIAGNOSES", "path": "%1$s/diagnoses.csv" }
                  ]
                }
                """.formatted(batchId);
        assertEquals(mapper.readTree(expected), mapper.readTree(actual));
    }
}
