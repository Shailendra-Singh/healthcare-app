package me.shail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** A change to a task: creation, a status change or an assignment, by the system or a person. */
@Entity
@Table(schema = "task", name = "task_event")
public class TaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    public Long id;

    @Column(name = "task_id", nullable = false)
    public Long taskId;

    @Column(nullable = false)
    public OffsetDateTime at;

    /** Null when the task was created */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    public Task.Status fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    public Task.Status toStatus;

    /** SYSTEM or a person */
    @Column(nullable = false, length = 100)
    public String actor;

    public String reason;

    public static TaskEvent of(Task task, Task.Status from, String actor, String reason) {
        TaskEvent event = new TaskEvent();
        event.taskId = task.id;
        event.at = OffsetDateTime.now();
        event.fromStatus = from;
        event.toStatus = task.status;
        event.actor = actor;
        event.reason = reason;
        return event;
    }
}
