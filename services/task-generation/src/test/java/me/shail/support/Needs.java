package me.shail.support;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Random;
import me.shail.client.CareNeed;
import net.datafaker.Faker;

/** Builds rules-engine care needs for tests. */
public final class Needs {

    public static final Faker FAKER = new Faker(Locale.US, new Random(20260929L));

    public static final CareNeed.TaskPolicy SPECIALIST = new CareNeed.TaskPolicy("scheduling", "referral");
    public static final CareNeed.TaskPolicy PRIMARY_CARE = new CareNeed.TaskPolicy("scheduling", "none");

    private Needs() {
    }

    public static String patientId() {
        return "P" + FAKER.number().digits(8);
    }

    /** Last visit {@code lastVisit} (null for never seen), cadence {@code everyDays}, as of {@code asOf}. */
    public static CareNeed need(String patientId, String programId, String tierId, String specialty, int everyDays,
            LocalDate lastVisit, LocalDate nextScheduled, LocalDate asOf, CareNeed.TaskPolicy policy) {
        LocalDate due = lastVisit == null ? asOf : lastVisit.plusDays(everyDays);
        String status = due.isAfter(asOf) ? "MET" : nextScheduled != null ? "SCHEDULED" : "OVERDUE";
        return new CareNeed(patientId, programId, tierId, specialty, everyDays, lastVisit, due, nextScheduled, status,
                "normal", null, policy);
    }

    public static CareNeed diabetes(String patientId, String specialty, int everyDays, LocalDate lastVisit,
            LocalDate nextScheduled, LocalDate asOf) {
        return need(patientId, "diabetes-management", "high-risk", specialty, everyDays, lastVisit, nextScheduled, asOf, SPECIALIST);
    }

    public static CareNeed wellness(String patientId, int everyDays, LocalDate lastVisit, LocalDate nextScheduled, LocalDate asOf) {
        return need(patientId, "primary-care-wellness", "high-priority", "PCP", everyDays, lastVisit, nextScheduled, asOf, PRIMARY_CARE);
    }
}
