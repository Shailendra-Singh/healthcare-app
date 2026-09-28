package me.shail.client;

/** The parts of clinical-data's {@code GET /api/v1/etl-runs/latest} the rules-engine records. */
public record EtlRun(Long runId, String status) {
}
