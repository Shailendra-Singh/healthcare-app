package me.shail.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import me.shail.model.EtlHeartbeat;

/**
 * @param outcome         waiting, unchanged, loaded, failed or busy
 * @param nextCheckDueBy  when the next check should have happened
 * @param stale           true once two checks in a row are overdue: the ETL is probably not running
 */
public record EtlHeartbeatDto(
        OffsetDateTime checkedAt,
        String outcome,
        String detail,
        int intervalSeconds,
        OffsetDateTime nextCheckDueBy,
        boolean stale) {

    public static EtlHeartbeatDto from(EtlHeartbeat heartbeat, Instant now) {
        if (heartbeat == null) {
            return null;
        }
        OffsetDateTime nextCheckDueBy = heartbeat.checkedAt.plusSeconds(heartbeat.intervalSeconds);
        boolean stale = now.isAfter(heartbeat.checkedAt.plusSeconds(2L * heartbeat.intervalSeconds).toInstant());
        return new EtlHeartbeatDto(heartbeat.checkedAt, heartbeat.outcome, heartbeat.detail,
                heartbeat.intervalSeconds, nextCheckDueBy, stale);
    }
}
