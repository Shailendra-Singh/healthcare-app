package me.shail.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import me.shail.client.CareNeed;
import me.shail.support.Needs;
import org.junit.jupiter.api.Test;

/** The task generation criteria, need by need. */
class TaskPlannerTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 9, 28);
    final String patient = Needs.patientId();

    // --- Primary Care Wellness ---

    @Test
    void pcpSeenBeforeAndPastCadenceGetsASchedulingTask() {
        TaskPlanner.Decision decision = TaskPlanner.decide(Needs.wellness(patient, 180, AS_OF.minusDays(200), null, AS_OF));

        assertEquals("SCHEDULING", decision.taskType());
        assertEquals("last visit " + AS_OF.minusDays(200) + " is older than the 180-day cadence", decision.reason());
    }

    @Test
    void pcpWithNoEncounterHistoryGetsNoTask() {
        TaskPlanner.Decision decision = TaskPlanner.decide(Needs.wellness(patient, 365, null, null, AS_OF));

        assertFalse(decision.createsTask());
        assertEquals("no prior encounter with PCP; program policy: no task", decision.reason());
    }

    // --- Diabetes Management ---

    @Test
    void specialtySeenBeforeAndPastCadenceGetsASchedulingTask() {
        assertEquals("SCHEDULING", TaskPlanner.decide(
                Needs.diabetes(patient, "Endocrinology", 90, AS_OF.minusDays(120), null, AS_OF)).taskType());
    }

    @Test
    void specialtyNeverSeenGetsAReferralTask() {
        TaskPlanner.Decision decision = TaskPlanner.decide(Needs.diabetes(patient, "Podiatry", 180, null, null, AS_OF));

        assertEquals("REFERRAL", decision.taskType());
        assertEquals("no prior encounter with Podiatry", decision.reason());
    }

    // --- Both programs ---

    @Test
    void aVisitWithinTheCadenceNeedsNoTask() {
        TaskPlanner.Decision decision = TaskPlanner.decide(Needs.diabetes(patient, "Endocrinology", 90, AS_OF.minusDays(30), null, AS_OF));

        assertFalse(decision.createsTask());
        assertEquals("within cadence: last visit " + AS_OF.minusDays(30) + ", next due " + AS_OF.plusDays(60), decision.reason());
    }

    @Test
    void aTaskStartsOnTheDueDateItself() {
        assertEquals("SCHEDULING", TaskPlanner.decide(
                Needs.diabetes(patient, "Endocrinology", 90, AS_OF.minusDays(90), null, AS_OF)).taskType());
    }

    @Test
    void aBookedAppointmentNeedsNoTask() {
        TaskPlanner.Decision seen = TaskPlanner.decide(Needs.diabetes(patient, "Cardiology", 90, AS_OF.minusDays(200), AS_OF.plusDays(5), AS_OF));
        TaskPlanner.Decision neverSeen = TaskPlanner.decide(Needs.diabetes(patient, "Nephrology", 180, null, AS_OF.plusDays(5), AS_OF));

        assertFalse(seen.createsTask());
        assertFalse(neverSeen.createsTask());
        assertEquals("appointment booked for " + AS_OF.plusDays(5), seen.reason());
    }

    @Test
    void aProgramWithoutTaskPolicyGetsNoTask() {
        CareNeed need = Needs.need(patient, "custom-program", "all", "Cardiology", 365, AS_OF.minusDays(400), null, AS_OF, null);

        assertNull(TaskPlanner.decide(need).taskType());
        assertEquals("program custom-program has no task policy", TaskPlanner.decide(need).reason());
    }

    @Test
    void anUnknownStatusIsAnError() {
        CareNeed need = new CareNeed(patient, "p", "t", "PCP", 1, null, AS_OF, null, "LATE", "normal", null, Needs.SPECIALIST);

        assertThrows(IllegalArgumentException.class, () -> TaskPlanner.decide(need));
    }
}
