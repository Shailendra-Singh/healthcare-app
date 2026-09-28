package me.shail.rules;

import java.util.List;

/**
 * A care program parsed from one YAML file in care-programs/.
 *
 * @param shortName optional
 * @param tiers     checked in order; the first whose criteria match wins
 */
public record ProgramDefinition(
        String id,
        String name,
        String shortName,
        String purpose,
        Condition eligibility,
        List<Tier> tiers) {

    public record Tier(String id, String name, Condition criteria, List<Need> needs) {
    }

    /**
     * A recurring visit the patient needs while in the tier.
     *
     * @param visit    specialty, matched case-insensitively against the encounter data
     * @param priority normal or high
     * @param note     optional
     */
    public record Need(String visit, int everyDays, String priority, String note) {
    }
}
