package me.shail.task;

import java.util.Locale;
import me.shail.client.CareNeed;

/**
 * Decides which task, if any, a care need calls for. Rules from the task generation criteria, driven by each
 * program's task policy in its care program file:
 * <ul>
 * <li>a need within its cadence (MET) or with a booked appointment (SCHEDULED) needs no task;</li>
 * <li>a due need (OVERDUE) the patient has seen that specialty for gets the policy's {@code pastCadence} task
 * (scheduling);</li>
 * <li>a due need the patient has never seen that specialty for gets the policy's {@code neverSeen} task
 * (referral for specialist programs, none for Primary Care Wellness).</li>
 * </ul>
 */
public final class TaskPlanner {

    private TaskPlanner() {
    }

    /**
     * @param taskType task type code (e.g. SCHEDULING), or null when the need calls for no task
     * @param reason   why, for the task history and close reasons
     */
    public record Decision(String taskType, String reason) {

        public boolean createsTask() {
            return taskType != null;
        }
    }

    public static Decision decide(CareNeed need) {
        switch (need.status()) {
            case "MET" -> {
                return new Decision(null, "within cadence: last visit " + need.lastVisitDate() + ", next due " + need.dueDate());
            }
            case "SCHEDULED" -> {
                return new Decision(null, "appointment booked for " + need.nextScheduledDate());
            }
            case "OVERDUE" -> {
                if (need.tasks() == null) {
                    return new Decision(null, "program " + need.programId() + " has no task policy");
                }
                boolean seenBefore = need.lastVisitDate() != null;
                String policy = seenBefore ? need.tasks().pastCadence() : need.tasks().neverSeen();
                String situation = seenBefore
                        ? "last visit " + need.lastVisitDate() + " is older than the " + need.everyDays() + "-day cadence"
                        : "no prior encounter with " + need.specialty();
                if (policy == null || policy.equals("none")) {
                    return new Decision(null, situation + "; program policy: no task");
                }
                return new Decision(policy.toUpperCase(Locale.ROOT), situation);
            }
            default -> throw new IllegalArgumentException("Unknown care need status: " + need.status());
        }
    }
}
