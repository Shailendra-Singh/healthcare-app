package me.shail.rules;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Reads the care program files. Called at the start of every evaluation, so edits to the folder take
 * effect on the next run without a redeploy.
 */
@ApplicationScoped
public class ProgramCatalog {

    @ConfigProperty(name = "rules-engine.programs-dir")
    Path programsDir;

    /**
     * @param yaml     file contents, stored with each result for auditing
     * @param checksum hex SHA-256 of the contents
     */
    public record LoadedProgram(ProgramDefinition definition, String sourceFile, String yaml, String checksum) {
    }

    public record LoadError(String sourceFile, List<String> errors) {
    }

    public record Load(List<LoadedProgram> programs, List<LoadError> errors) {
    }

    /** Every *.yaml / *.yml file, in name order. A file that fails validation is reported, not loaded. */
    public Load load() {
        List<LoadedProgram> programs = new ArrayList<>();
        List<LoadError> errors = new ArrayList<>();
        if (!Files.isDirectory(programsDir)) {
            errors.add(new LoadError(programsDir.toString(), List.of("care programs folder not found")));
            return new Load(programs, errors);
        }

        Map<String, String> fileById = new HashMap<>();
        for (Path file : programFiles()) {
            String name = file.getFileName().toString();
            try {
                String yaml = Files.readString(file, StandardCharsets.UTF_8);
                LoadedProgram program = parse(name, yaml);
                String earlier = fileById.putIfAbsent(program.definition().id(), name);
                if (earlier != null) {
                    errors.add(new LoadError(name, List.of(
                            "id: '" + program.definition().id() + "' is already defined in " + earlier)));
                } else {
                    programs.add(program);
                }
            } catch (ProgramDefinitionException e) {
                errors.add(new LoadError(name, e.errors()));
            } catch (IOException e) {
                errors.add(new LoadError(name, List.of("could not read file: " + e.getMessage())));
            }
        }
        return new Load(programs, errors);
    }

    /** Parses a file's contents; also used to reload the last good version of a file that now fails. */
    public static LoadedProgram parse(String sourceFile, String yaml) {
        return new LoadedProgram(ProgramParser.parse(yaml), sourceFile, yaml, sha256(yaml));
    }

    private List<Path> programFiles() {
        try (Stream<Path> files = Files.list(programsDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().matches(".*\\.ya?ml"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not list " + programsDir, e);
        }
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
