package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import jakarta.data.page.PageRequest;
import jakarta.persistence.NoResultException;
import java.time.OffsetDateTime;
import java.util.List;
import me.shail.dto.LoadFileDto;
import me.shail.dto.LoadRunDto;
import me.shail.model.LoadFile;
import me.shail.model.LoadRun;
import me.shail.repository.LoadFileRepository;
import me.shail.repository.LoadRejectRepository;
import me.shail.repository.LoadRunRepository;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoadRunServiceTest {

    @Mock
    LoadRunRepository loadRunRepository;

    @Mock
    LoadFileRepository loadFileRepository;

    @Mock
    LoadRejectRepository loadRejectRepository;

    @InjectMocks
    LoadRunService service;

    @Test
    void findLatestCombinesTheRunItsFilesAndRejectCounts() {
        LoadRun run = run(12L, "SUCCEEDED");
        LoadFile labs = file(12L, "labs.csv", 100);
        LoadFile patients = file(12L, "patients.csv", 30);
        when(loadRunRepository.findLatest()).thenReturn(Uni.createFrom().item(run));
        when(loadFileRepository.findByRun(12L)).thenReturn(Uni.createFrom().item(List.of(labs, patients)));
        when(loadRejectRepository.countByFile(12L))
                .thenReturn(Uni.createFrom().item(List.<Object[]>of(new Object[] {"labs.csv", 4L})));

        LoadRunDto latest = service.findLatest().await().indefinitely();

        assertEquals(12L, latest.runId());
        assertEquals("SUCCEEDED", latest.status());
        assertEquals(List.of(LoadFileDto.from(labs, 4), LoadFileDto.from(patients, 0)), latest.files());
        assertEquals(4, latest.rejectedRows());
    }

    @Test
    void findLatestReportsTheFailureOfAFailedRun() {
        LoadRun run = run(3L, "FAILED");
        run.errorMessage = "BadCopyFileFormat: column name mismatch";
        when(loadRunRepository.findLatest()).thenReturn(Uni.createFrom().item(run));
        when(loadFileRepository.findByRun(3L)).thenReturn(Uni.createFrom().item(List.of()));
        when(loadRejectRepository.countByFile(3L)).thenReturn(Uni.createFrom().item(List.of()));

        LoadRunDto latest = service.findLatest().await().indefinitely();

        assertEquals("FAILED", latest.status());
        assertEquals(run.errorMessage, latest.errorMessage());
        assertEquals(0, latest.rejectedRows());
    }

    @Test
    void findPageAddsEachRunsFilesInOrder() {
        LoadRun newer = run(8L, "SUCCEEDED");
        LoadRun older = run(7L, "FAILED");
        LoadFile labs = file(8L, "labs.csv", 50);
        when(loadRunRepository.findNewestFirst(PageRequest.ofPage(2, 5, false)))
                .thenReturn(Uni.createFrom().item(List.of(newer, older)));
        when(loadFileRepository.findByRun(8L)).thenReturn(Uni.createFrom().item(List.of(labs)));
        when(loadFileRepository.findByRun(7L)).thenReturn(Uni.createFrom().item(List.of()));
        when(loadRejectRepository.countByFile(8L))
                .thenReturn(Uni.createFrom().item(List.<Object[]>of(new Object[] {"labs.csv", 1L})));
        when(loadRejectRepository.countByFile(7L)).thenReturn(Uni.createFrom().item(List.of()));

        List<LoadRunDto> page = service.findPage(2, 5).await().indefinitely();

        assertEquals(List.of(8L, 7L), page.stream().map(LoadRunDto::runId).toList());
        assertEquals(List.of(LoadFileDto.from(labs, 1)), page.getFirst().files());
        assertEquals(1, page.getFirst().rejectedRows());
        assertEquals(List.of(), page.get(1).files());
    }

    @Test
    void findPageIsEmptyWhenTheEtlHasNeverRun() {
        when(loadRunRepository.findNewestFirst(PageRequest.ofPage(1, 20, false)))
                .thenReturn(Uni.createFrom().item(List.of()));

        assertEquals(List.of(), service.findPage(1, 20).await().indefinitely());
        verifyNoInteractions(loadFileRepository, loadRejectRepository);
    }

    @Test
    void findLatestReturnsNullWhenTheEtlHasNeverRun() {
        when(loadRunRepository.findLatest()).thenReturn(Uni.createFrom().failure(new NoResultException()));

        assertNull(service.findLatest().await().indefinitely());
        verify(loadRunRepository).findLatest();
        verifyNoInteractions(loadFileRepository, loadRejectRepository);
    }

    private static LoadRun run(long id, String status) {
        LoadRun run = new LoadRun();
        run.id = id;
        run.status = status;
        run.startedAt = OffsetDateTime.now().minusMinutes(2);
        run.finishedAt = OffsetDateTime.now();
        return run;
    }

    private static LoadFile file(long runId, String name, int rows) {
        LoadFile file = new LoadFile();
        file.runId = runId;
        file.fileName = name;
        file.checksum = TestData.FAKER.hashing().sha256();
        file.fileSize = 1024L;
        file.rowCount = rows;
        return file;
    }
}
