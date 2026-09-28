-- Evaluations now also start when clinical-data finishes a new successful ETL load (DATA_CHANGED).
ALTER TABLE eval.evaluation_run DROP CONSTRAINT ck_evaluation_run_trigger;
ALTER TABLE eval.evaluation_run ADD CONSTRAINT ck_evaluation_run_trigger
    CHECK (trigger IN ('SCHEDULED', 'STARTUP', 'MANUAL', 'DATA_CHANGED'));
