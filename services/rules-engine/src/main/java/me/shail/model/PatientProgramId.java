package me.shail.model;

import java.io.Serializable;

/** Primary key of {@link PatientProgram}. */
public record PatientProgramId(Long runId, String sourcePatientId, String programId) implements Serializable {
}
