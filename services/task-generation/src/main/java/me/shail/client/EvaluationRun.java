package me.shail.client;

import java.time.LocalDate;

/** The parts of a rules-engine evaluation run that task-generation uses. */
public record EvaluationRun(Long runId, LocalDate asOfDate, String status) {
}
