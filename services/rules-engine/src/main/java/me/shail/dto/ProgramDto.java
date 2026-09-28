package me.shail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import me.shail.rules.ProgramCatalog;
import me.shail.rules.ProgramDefinition;

/** A care program as currently defined in care-programs/. Criteria are in the file itself. */
public record ProgramDto(
        String id,
        String name,
        @JsonInclude(JsonInclude.Include.NON_NULL) String shortName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String purpose,
        String sourceFile,
        String checksum,
        List<Tier> tiers) {

    public record Tier(String id, String name, List<Need> needs) {
    }

    public record Need(String visit, int everyDays, String priority,
            @JsonInclude(JsonInclude.Include.NON_NULL) String note) {
    }

    public static ProgramDto from(ProgramCatalog.LoadedProgram program) {
        ProgramDefinition definition = program.definition();
        return new ProgramDto(definition.id(), definition.name(), definition.shortName(), definition.purpose(),
                program.sourceFile(), program.checksum(),
                definition.tiers().stream()
                        .map(t -> new Tier(t.id(), t.name(), t.needs().stream()
                                .map(n -> new Need(n.visit(), n.everyDays(), n.priority(), n.note()))
                                .toList()))
                        .toList());
    }
}
