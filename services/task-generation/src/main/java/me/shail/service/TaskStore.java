package me.shail.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import me.shail.client.CareNeed;
import me.shail.model.GenerationRun;
import me.shail.model.Task;
import me.shail.model.TaskEvent;
import me.shail.repository.GenerationRunRepository;
import me.shail.repository.TaskEventRepository;
import me.shail.repository.TaskRepository;
import me.shail.task.TaskPlanner;

/**
 * The transactional steps of task generation. Each page of care needs is reconciled in its own transaction.
 */
@ApplicationScoped
public class TaskStore {

    @Inject
    TaskRepository taskRepository;

    @Inject
    TaskEventRepository taskEventRepository;

    @Inject
    GenerationRunRepository generationRunRepository;

    /** Tasks created, updated and closed. */
    public record Counts(int created, int updated, int closed) {

        public Counts plus(Counts other) {
            return new Counts(created + other.created, updated + other.updated, closed + other.closed);
        }
    }

    /** @throws GenerationInProgressException when a run is already RUNNING */
    @Transactional
    public GenerationRun createRun(GenerationRun.Trigger trigger, Long evaluationRunId) {
        if (generationRunRepository.countByStatus(GenerationRun.Status.RUNNING) > 0) {
            throw new GenerationInProgressException();
        }
        GenerationRun run = new GenerationRun();
        run.trigger = trigger;
        run.evaluationRunId = evaluationRunId;
        run.status = GenerationRun.Status.RUNNING;
        run.startedAt = OffsetDateTime.now();
        generationRunRepository.insert(run);
        return run;
    }

    /**
     * Brings the tasks of the patients on this page in line with their care needs from the evaluation run:
     * creates tasks for due needs that call for one, updates tasks that are still called for, and closes tasks
     * whose need is met, booked or now calls for a different task type.
     */
    @Transactional
    public Counts reconcilePage(Long evaluationRunId, List<CareNeed> needs) {
        List<String> patientIds = needs.stream().map(CareNeed::sourcePatientId).distinct().toList();
        Map<String, List<Task>> activeByNeed = new HashMap<>();
        for (Task task : taskRepository.findByPatientsAndStatuses(patientIds, TaskRepository.ACTIVE)) {
            activeByNeed.computeIfAbsent(needKey(task.sourcePatientId, task.programId, task.specialty), k -> new ArrayList<>())
                    .add(task);
        }

        int created = 0;
        int updated = 0;
        int closed = 0;
        List<TaskEvent> events = new ArrayList<>();
        for (CareNeed need : needs) {
            TaskPlanner.Decision decision = TaskPlanner.decide(need);
            List<Task> active = activeByNeed.getOrDefault(needKey(need.sourcePatientId(), need.programId(), need.specialty()), List.of());

            Task current = null;
            for (Task task : active) {
                if (decision.createsTask() && task.taskType.equals(decision.taskType())) {
                    current = task;
                } else {
                    String reason = decision.createsTask()
                            ? "replaced by a " + decision.taskType() + " task: " + decision.reason()
                            : decision.reason();
                    events.add(resolve(task, reason));
                    closed++;
                }
            }

            if (!decision.createsTask()) {
                continue;
            }
            if (current != null) {
                if (refresh(current, need, evaluationRunId)) {
                    updated++;
                }
                taskRepository.update(current);
            } else if (!taskRepository.closedByPersonForSameGap(need.sourcePatientId(), need.programId(),
                    need.specialty(), decision.taskType(), need.lastVisitDate())) {
                Task task = newTask(need, decision.taskType(), evaluationRunId);
                taskRepository.insert(task);
                events.add(TaskEvent.of(task, null, Task.SYSTEM,
                        "created from evaluation run " + evaluationRunId + ": " + decision.reason()));
                created++;
            }
        }
        taskEventRepository.insertAll(events);
        return new Counts(created, updated, closed);
    }

    /**
     * Closes active tasks that this evaluation run did not call for at all: the patient left the program or tier,
     * or the need is gone from the program.
     */
    @Transactional
    public int closeTasksNoLongerNeeded(Long evaluationRunId) {
        List<Task> leftovers = taskRepository.findByStatusesNotSeenSince(TaskRepository.ACTIVE, evaluationRunId);
        List<TaskEvent> events = new ArrayList<>();
        for (Task task : leftovers) {
            events.add(resolve(task, "no longer needed: evaluation run " + evaluationRunId
                    + " has no " + task.specialty + " need for this patient in " + task.programId));
        }
        taskEventRepository.insertAll(events);
        return leftovers.size();
    }

    @Transactional
    public GenerationRun complete(Long runId, int needsRead, Counts counts) {
        GenerationRun run = generationRunRepository.findById(runId).orElseThrow();
        run.status = GenerationRun.Status.SUCCEEDED;
        run.needsRead = needsRead;
        run.tasksCreated = counts.created();
        run.tasksUpdated = counts.updated();
        run.tasksClosed = counts.closed();
        run.finishedAt = OffsetDateTime.now();
        generationRunRepository.update(run);
        return run;
    }

    @Transactional
    public GenerationRun fail(Long runId, String errorMessage) {
        GenerationRun run = generationRunRepository.findById(runId).orElseThrow();
        run.status = GenerationRun.Status.FAILED;
        run.errorMessage = errorMessage.length() > 4000 ? errorMessage.substring(0, 4000) : errorMessage;
        run.finishedAt = OffsetDateTime.now();
        generationRunRepository.update(run);
        return run;
    }

    @Transactional
    public int failAbandonedRuns() {
        return generationRunRepository.failAbandoned(OffsetDateTime.now());
    }

    /** The evaluation run the latest successful reconciliation processed. */
    @Transactional
    public Optional<Long> latestReconciledEvaluationRun() {
        return generationRunRepository.findLatestByStatus(GenerationRun.Status.SUCCEEDED).map(run -> run.evaluationRunId);
    }

    private TaskEvent resolve(Task task, String reason) {
        Task.Status from = task.status;
        task.status = Task.Status.RESOLVED;
        task.closedAt = OffsetDateTime.now();
        task.closedBy = Task.SYSTEM;
        task.closeReason = reason;
        task.updatedAt = task.closedAt;
        taskRepository.update(task);
        return TaskEvent.of(task, from, Task.SYSTEM, reason);
    }

    /** Copies the latest need onto a task; true when anything but the run id changed. */
    private static boolean refresh(Task task, CareNeed need, Long evaluationRunId) {
        // A referral's due date is when it first became due; the need's due date for a never-seen specialty is
        // always "today", so it is only taken over for scheduling gaps (last visit + cadence)
        var dueDate = need.lastVisitDate() == null ? task.dueDate : need.dueDate();
        boolean changed = !Objects.equals(task.tierId, need.tierId())
                || !Objects.equals(task.priority, need.priority())
                || !Objects.equals(task.cadenceDays, need.everyDays())
                || !Objects.equals(task.dueDate, dueDate)
                || !Objects.equals(task.lastVisitDate, need.lastVisitDate())
                || !Objects.equals(task.note, need.note());
        task.tierId = need.tierId();
        task.priority = need.priority();
        task.cadenceDays = need.everyDays();
        task.dueDate = dueDate;
        task.lastVisitDate = need.lastVisitDate();
        task.note = need.note();
        task.lastEvaluationRunId = evaluationRunId;
        if (changed) {
            task.updatedAt = OffsetDateTime.now();
        }
        return changed;
    }

    private static Task newTask(CareNeed need, String taskType, Long evaluationRunId) {
        Task task = new Task();
        task.sourcePatientId = need.sourcePatientId();
        task.programId = need.programId();
        task.tierId = need.tierId();
        task.specialty = need.specialty();
        task.taskType = taskType;
        task.status = Task.Status.OPEN;
        task.priority = need.priority();
        task.cadenceDays = need.everyDays();
        task.dueDate = need.dueDate();
        task.lastVisitDate = need.lastVisitDate();
        task.note = need.note();
        task.firstEvaluationRunId = evaluationRunId;
        task.lastEvaluationRunId = evaluationRunId;
        task.createdAt = OffsetDateTime.now();
        task.updatedAt = task.createdAt;
        return task;
    }

    private static String needKey(String patientId, String programId, String specialty) {
        return patientId + "\u0000" + programId + "\u0000" + specialty.toLowerCase(java.util.Locale.ROOT);
    }
}
