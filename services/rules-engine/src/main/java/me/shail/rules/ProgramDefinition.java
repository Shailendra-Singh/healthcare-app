package me.shail.rules;

import java.util.List;

/**
 * A care program parsed from one YAML file in care-programs/.
 *
 * @param shortName optional
 * @param tiers     checked in order; the first whose criteria match wins
 * @param tasks     how task-generation turns this program's unmet needs into tasks; null for none
 */
public record ProgramDefinition(
        String id,
        String name,
        String shortName,
        String purpose,
        Condition eligibility,
        List<Tier> tiers,
        TaskPolicy tasks) {

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

    /**
     * The task to create for a due need, by whether the patient has seen that specialty before.
     * Values are task types known to task-generation (scheduling, referral) or none.
     *
     * @param pastCadence seen before and the last visit is older than the cadence
     * @param neverSeen   no visit to that specialty on record
     */
    public record TaskPolicy(String pastCadence, String neverSeen) {

        public static final java.util.Set<String> VALUES = java.util.Set.of("scheduling", "referral", "none");
    }
}
