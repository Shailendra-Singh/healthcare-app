package me.shail.healthplatform.ingestion.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * The four source files ingestion accepts, each with its fixed file name.
 * Constant names match the file_type check constraint in batch_file.
 */
public enum FileType {
    PATIENTS("patients.csv"),
    DIAGNOSES("diagnoses.csv"),
    LABS("labs.csv"),
    ENCOUNTERS("encounters.csv");

    private final String fileName;

    FileType(String fileName) {
        this.fileName = fileName;
    }

    public String fileName() {
        return fileName;
    }

    /**
     * The type for an exact file name, or empty for any other file.
     */
    public static Optional<FileType> fromFileName(String name) {
        return Arrays.stream(values())
                .filter(type -> type.fileName.equals(name))
                .findFirst();
    }
}
