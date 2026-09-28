package me.shail.model;

import java.io.Serializable;

/** Primary key of {@link CareNeed}. */
public record CareNeedId(Long runId, String sourcePatientId, String programId, String specialty) implements Serializable {
}
