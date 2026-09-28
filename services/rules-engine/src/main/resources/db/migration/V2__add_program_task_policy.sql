-- The task policy of each program version (the program file's `tasks:` section), so every care need can
-- say which task it calls for under the exact rules that produced it. Null when the file has no policy.
ALTER TABLE eval.program_version
    ADD COLUMN past_cadence_task VARCHAR(30),
    ADD COLUMN never_seen_task   VARCHAR(30),
    ADD CONSTRAINT ck_program_version_past_cadence_task
        CHECK (past_cadence_task IS NULL OR past_cadence_task IN ('scheduling', 'referral', 'none')),
    ADD CONSTRAINT ck_program_version_never_seen_task
        CHECK (never_seen_task IS NULL OR never_seen_task IN ('scheduling', 'referral', 'none'));
