package me.shail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A kind of task (SCHEDULING, REFERRAL); rows are reference data seeded by migrations. */
@Entity
@Table(schema = "task", name = "task_type")
public class TaskType {

    @Id
    @Column(length = 30)
    public String code;

    @Column(nullable = false, length = 100)
    public String name;

    @Column(nullable = false)
    public String description;
}
