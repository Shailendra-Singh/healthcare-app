package me.shail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import me.shail.dto.EtlHeartbeatDto;
import me.shail.model.EtlHeartbeat;
import me.shail.repository.EtlHeartbeatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EtlHeartbeatServiceTest {

    static final OffsetDateTime CHECKED_AT = OffsetDateTime.of(2026, 9, 28, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    EtlHeartbeatRepository etlHeartbeatRepository;

    @InjectMocks
    EtlHeartbeatService service;

    @Test
    void findReadsTheSingleRowAndJudgesStalenessWithTheClock() {
        when(etlHeartbeatRepository.findById(EtlHeartbeat.ID)).thenReturn(Uni.createFrom().item(heartbeat()));

        service.clock = Clock.fixed(CHECKED_AT.plusMinutes(4).toInstant(), ZoneOffset.UTC);
        EtlHeartbeatDto recent = service.find().await().indefinitely();
        service.clock = Clock.fixed(CHECKED_AT.plusMinutes(11).toInstant(), ZoneOffset.UTC);
        EtlHeartbeatDto overdue = service.find().await().indefinitely();

        assertEquals("loaded", recent.outcome());
        assertFalse(recent.stale());
        assertTrue(overdue.stale());
    }

    @Test
    void findReturnsNullBeforeTheFirstCheck() {
        when(etlHeartbeatRepository.findById(EtlHeartbeat.ID)).thenReturn(Uni.createFrom().nullItem());

        assertNull(service.find().await().indefinitely());
    }

    private static EtlHeartbeat heartbeat() {
        EtlHeartbeat heartbeat = new EtlHeartbeat();
        heartbeat.id = EtlHeartbeat.ID;
        heartbeat.checkedAt = CHECKED_AT;
        heartbeat.outcome = "loaded";
        heartbeat.detail = "Run 4 SUCCEEDED with 0 rejected rows";
        heartbeat.intervalSeconds = 300;
        return heartbeat;
    }
}
