package me.shail.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import me.shail.model.LoadFile;
import me.shail.model.LoadRun;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

class LoadRunDtoTest {

    @Test
    void mapsTheRunAndSumsRejectsOverItsFiles() {
        LoadRun run = new LoadRun();
        run.id = 7L;
        run.status = "SUCCEEDED";
        run.startedAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        run.finishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<LoadFileDto> files = List.of(
                LoadFileDto.from(file(7L, "labs.csv", 120), 3),
                LoadFileDto.from(file(7L, "patients.csv", 40), 2));

        LoadRunDto dto = LoadRunDto.from(run, files);

        assertEquals(7L, dto.runId());
        assertEquals("SUCCEEDED", dto.status());
        assertEquals(run.startedAt, dto.startedAt());
        assertEquals(run.finishedAt, dto.finishedAt());
        assertNull(dto.errorMessage());
        assertEquals(5, dto.rejectedRows());
        assertEquals(files, dto.files());
    }

    @Test
    void fileDtoCarriesChecksumSizeAndCounts() {
        LoadFile file = file(1L, "labs.csv", 120);

        assertEquals(new LoadFileDto("labs.csv", file.checksum, file.fileSize, 120, 4), LoadFileDto.from(file, 4));
    }

    @Test
    void nullMapsToNull() {
        assertNull(LoadRunDto.from(null, List.of()));
        assertNull(LoadFileDto.from(null, 0));
    }

    static LoadFile file(long runId, String name, int rows) {
        LoadFile file = new LoadFile();
        file.runId = runId;
        file.fileName = name;
        file.checksum = TestData.FAKER.hashing().sha256();
        file.fileSize = (long) TestData.FAKER.number().numberBetween(100, 100_000);
        file.rowCount = rows;
        return file;
    }
}
