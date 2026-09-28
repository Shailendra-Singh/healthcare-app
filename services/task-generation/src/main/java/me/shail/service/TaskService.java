package me.shail.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import me.shail.dto.GenerationRunDto;
import me.shail.dto.TaskDto;
import me.shail.dto.TaskEventDto;
import me.shail.dto.TaskTypeDto;
import me.shail.dto.TaskUpdateRequest;
import me.shail.model.Task;
import me.shail.model.TaskEvent;
import me.shail.repository.GenerationRunRepository;
import me.shail.repository.TaskEventRepository;
import me.shail.repository.TaskRepository;
import me.shail.repository.TaskTypeRepository;

/** Reading tasks, and the changes people make to them. */
@ApplicationScoped
@Transactional
public class TaskService {

    /** What a person may change a task's status to, from each active status. RESOLVED is the system's. */
    static final Map<Task.Status, Set<Task.Status>> TRANSITIONS = Map.of(
            Task.Status.OPEN, Set.of(Task.Status.IN_PROGRESS, Task.Status.COMPLETED, Task.Status.CANCELLED),
            Task.Status.IN_PROGRESS, Set.of(Task.Status.OPEN, Task.Status.COMPLETED, Task.Status.CANCELLED));

    @Inject
    TaskRepository taskRepository;

    @Inject
    TaskEventRepository taskEventRepository;

    @Inject
    TaskTypeRepository taskTypeRepository;

    @Inject
    GenerationRunRepository generationRunRepository;

    public List<TaskDto> search(List<Task.Status> statuses, String taskType, String programId, String specialty,
            String assignee, int page, int size) {
        return taskRepository.search(statuses, taskType, programId, specialty, assignee, page, size)
                .stream().map(TaskDto::from).toList();
    }

    /** With its history. */
    public Optional<TaskDto> find(Long taskId) {
        return taskRepository.findById(taskId).map(task -> TaskDto.from(task,
                taskEventRepository.findByTask(taskId).stream().map(TaskEventDto::from).toList()));
    }

    /** Every task of a patient, open and closed, newest first. */
    public List<TaskDto> forPatient(String sourcePatientId) {
        return taskRepository.findByPatient(sourcePatientId).stream().map(TaskDto::from).toList();
    }

    public List<TaskTypeDto> taskTypes() {
        return taskTypeRepository.findAll().stream().map(TaskTypeDto::from).toList();
    }

    public Optional<GenerationRunDto> latestRun() {
        return generationRunRepository.findLatest().map(GenerationRunDto::from);
    }

    public Optional<GenerationRunDto> run(Long runId) {
        return generationRunRepository.findById(runId).map(GenerationRunDto::from);
    }

    /**
     * Applies a person's change. Empty when there is no such task.
     *
     * @throws InvalidTaskChangeException for a status that cannot follow the current one, or a closed task
     */
    public Optional<TaskDto> update(Long taskId, TaskUpdateRequest change) {
        Optional<Task> found = taskRepository.findById(taskId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Task task = found.get();
        if (!task.status.isActive()) {
            throw new InvalidTaskChangeException("Task " + taskId + " is " + task.status + " and can no longer change", true);
        }
        if (change.status() == null && change.assignee() == null) {
            throw new InvalidTaskChangeException("Nothing to change: give a status, an assignee or both", false);
        }

        Task.Status from = task.status;
        OffsetDateTime now = OffsetDateTime.now();
        if (change.status() != null && change.status() != task.status) {
            if (!TRANSITIONS.get(task.status).contains(change.status())) {
                throw new InvalidTaskChangeException("A " + task.status + " task can change to "
                        + TRANSITIONS.get(task.status).stream().map(Enum::name).sorted().toList() + ", not " + change.status(), false);
            }
            task.status = change.status();
            if (!task.status.isActive()) {
                task.closedAt = now;
                task.closedBy = change.actor();
                task.closeReason = change.reason();
            }
        }
        String reason = change.reason();
        if (change.assignee() != null) {
            String assignee = change.assignee().isBlank() ? null : change.assignee().trim();
            if (!java.util.Objects.equals(assignee, task.assignee)) {
                task.assignee = assignee;
                String assignment = assignee == null ? "unassigned" : "assigned to " + assignee;
                reason = reason == null ? assignment : assignment + ": " + reason;
            }
        }
        task.updatedAt = now;
        taskRepository.update(task);
        taskEventRepository.insert(TaskEvent.of(task, from, change.actor(), reason));
        return find(taskId);
    }
}
