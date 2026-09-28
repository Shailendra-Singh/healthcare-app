package me.shail.dto;

import java.util.List;
import me.shail.rules.ProgramCatalog;

/**
 * The care-programs/ folder as it is now: programs that load, and files that fail validation.
 * The next evaluation uses exactly this (with a failing file's last good version, if it has one).
 */
public record ProgramCatalogDto(List<ProgramDto> programs, List<FileError> errors) {

    public record FileError(String sourceFile, List<String> errors) {
    }

    public static ProgramCatalogDto from(ProgramCatalog.Load load) {
        return new ProgramCatalogDto(
                load.programs().stream().map(ProgramDto::from).toList(),
                load.errors().stream().map(e -> new FileError(e.sourceFile(), e.errors())).toList());
    }
}
