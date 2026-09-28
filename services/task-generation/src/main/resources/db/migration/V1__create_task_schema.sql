-- Care tasks generated from the rules-engine's care needs, and worked by people through the API.
--
-- After each successful rules-engine evaluation, task-generation reconciles: a due need whose program
-- policy calls for a task gets exactly one active task (per patient, program, specialty and type); a task
-- whose need is met, booked or gone is closed. Tasks are never deleted; closed ones stay as history.

CREATE SCHEMA IF NOT EXISTS task;

-- Task kinds as data, so a new kind is a row here plus a value in a program's task policy
CREATE TABLE task.task_type (
    code        VARCHAR(30)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT         NOT NULL
);

INSERT INTO task.task_type (code, name, description) VALUES
    ('SCHEDULING', 'Scheduling', 'Book a visit with a specialty the patient has seen before; the last visit is older than the cadence.'),
    ('REFERRAL',   'Referral',   'Refer the patient to a specialty they have no prior encounter with.');

CREATE TABLE task.task (
    task_id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_patient_id       VARCHAR(64)  NOT NULL,      -- patient_id from patients.csv (details: clinical-data)
    program_id              VARCHAR(100) NOT NULL,
    tier_id                 VARCHAR(100) NOT NULL,
    specialty               VARCHAR(100) NOT NULL,
    task_type               VARCHAR(30)  NOT NULL REFERENCES task.task_type (code),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    priority                VARCHAR(10)  NOT NULL DEFAULT 'normal',
    cadence_days            INTEGER      NOT NULL,
    due_date                DATE         NOT NULL,
    last_visit_date         DATE,                       -- null for referrals
    note                    TEXT,
    assignee                VARCHAR(100),
    first_evaluation_run_id BIGINT       NOT NULL,      -- rules-engine run that created it
    last_evaluation_run_id  BIGINT       NOT NULL,      -- last run that still called for it
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at               TIMESTAMPTZ,
    closed_by               VARCHAR(100),               -- SYSTEM or the person who closed it
    close_reason            TEXT,
    version                 INTEGER      NOT NULL DEFAULT 0,  -- optimistic locking: people and reconciliation
    CONSTRAINT ck_task_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'RESOLVED')),
    CONSTRAINT ck_task_priority CHECK (priority IN ('normal', 'high')),
    CONSTRAINT ck_task_cadence CHECK (cadence_days > 0),
    CONSTRAINT ck_task_closed CHECK ((status IN ('OPEN', 'IN_PROGRESS')) = (closed_at IS NULL))
);

-- At most one active task per patient, program, specialty and type: reconciliation updates it
CREATE UNIQUE INDEX ux_task_active ON task.task (source_patient_id, program_id, specialty, task_type)
    WHERE status IN ('OPEN', 'IN_PROGRESS');
-- Work lists: active tasks, most urgent first
CREATE INDEX ix_task_status_due ON task.task (status, due_date, priority);
CREATE INDEX ix_task_patient ON task.task (source_patient_id);
-- Closing tasks the latest evaluation no longer calls for
CREATE INDEX ix_task_last_evaluation ON task.task (last_evaluation_run_id) WHERE status IN ('OPEN', 'IN_PROGRESS');
-- A person's decision on a gap is kept: a closed task suppresses re-creating the same gap (same last visit)
CREATE INDEX ix_task_closed_gap ON task.task (source_patient_id, program_id, specialty, task_type, last_visit_date)
    WHERE status IN ('COMPLETED', 'CANCELLED');

-- Every status change, by the system or a person
CREATE TABLE task.task_event (
    event_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id     BIGINT       NOT NULL REFERENCES task.task (task_id),
    at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    from_status VARCHAR(20),                            -- null when the task was created
    to_status   VARCHAR(20)  NOT NULL,
    actor       VARCHAR(100) NOT NULL,                  -- SYSTEM or a person
    reason      TEXT
);
CREATE INDEX ix_task_event_task ON task.task_event (task_id, event_id);

-- One reconciliation against a rules-engine evaluation run
CREATE TABLE task.generation_run (
    run_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trigger           VARCHAR(20) NOT NULL,
    evaluation_run_id BIGINT      NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    needs_read        INTEGER,
    tasks_created     INTEGER,
    tasks_updated     INTEGER,
    tasks_closed      INTEGER,
    error_message     TEXT,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at       TIMESTAMPTZ,
    CONSTRAINT ck_generation_run_trigger CHECK (trigger IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT ck_generation_run_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED'))
);
-- Only one reconciliation at a time
CREATE UNIQUE INDEX ux_generation_run_single_active ON task.generation_run ((TRUE)) WHERE status = 'RUNNING';
CREATE INDEX ix_generation_run_evaluation ON task.generation_run (status, evaluation_run_id DESC);
