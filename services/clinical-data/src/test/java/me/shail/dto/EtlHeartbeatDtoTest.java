package me.shail.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import me.shail.model.EtlHeartbeat;
import me.shail.support.TestData;
import org.junit.jupiter.api.Test;

class EtlHeartbeatDtoTest {

    static final OffsetDateTime CHECKED_AT = OffsetDateTime.of(2026, 9, 28, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void mapsTheHeartbeatAndWhenTheNextCheckIsDue() {
        EtlHeartbeat heartbeat = heartbeat(300);

        EtlHeartbeatDto dto = EtlHeartbeatDto.from(heartbeat, CHECKED_AT.plusSeconds(10).toInstant());

        assertEquals(CHECKED_AT, dto.checkedAt());
        assertEquals("unchanged", dto.outcome());
        assertEquals(heartbeat.detail, dto.detail());
        assertEquals(300, dto.intervalSeconds());
        assertEquals(CHECKED_AT.plusMinutes(5), dto.nextCheckDueBy());
        assertFalse(dto.stale());
    }

    @Test
    void oneLateCheckIsNotYetStale() {
        assertFalse(EtlHeartbeatDto.from(heartbeat(300), at(CHECKED_AT.plusMinutes(9))).stale());
        assertFalse(EtlHeartbeatDto.from(heartbeat(300), at(CHECKED_AT.plusMinutes(10))).stale());
    }

    @Test
    void twoMissedChecksInARowAreStale() {
        assertTrue(EtlHeartbeatDto.from(heartbeat(300), at(CHECKED_AT.plusMinutes(10).plusSeconds(1))).stale());
    }

    @Test
    void nullMapsToNull() {
        assertNull(EtlHeartbeatDto.from(null, Instant.now()));
    }

    static EtlHeartbeat heartbeat(int intervalSeconds) {
        EtlHeartbeat heartbeat = new EtlHeartbeat();
        heartbeat.id = EtlHeartbeat.ID;
        heartbeat.checkedAt = CHECKED_AT;
        heartbeat.outcome = "unchanged";
        heartbeat.detail = "Files unchanged since run " + TestData.FAKER.number().numberBetween(1, 500);
        heartbeat.intervalSeconds = intervalSeconds;
        return heartbeat;
    }

    private static Instant at(OffsetDateTime time) {
        return time.toInstant();
    }
}
