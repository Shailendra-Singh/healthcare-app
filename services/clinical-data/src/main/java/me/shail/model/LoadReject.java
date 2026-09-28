package me.shail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A CSV row the merge procedure could not promote into dbo. Only the columns the API needs are mapped.
 */
@Entity
@Table(schema = "etl", name = "load_reject")
public class LoadReject {

    @Id
    @Column(name = "reject_id")
    public Long id;

    @Column(name = "run_id", nullable = false)
    public Long runId;

    @Column(name = "file_name", nullable = false, length = 100)
    public String fileName;

    @Column(nullable = false)
    public String reason;
}
