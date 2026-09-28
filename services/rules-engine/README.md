# rules-engine

Evaluates every patient in clinical-data against the care programs in `care-programs/` (repo root) and
stores each patient's programs, tier and care needs in its own `rules_engine` database.

- **Runs** daily (`EVALUATION_CRON`, default 06:00), on startup when today has no successful run yet,
  and on demand with `POST /api/v1/evaluations`.
- **Rules** are re-read from `care-programs/` at the start of every run: edit, add or remove a YAML file
  and the next run uses it, no redeploy. A file that fails validation is reported and its last good
  version is used. See `care-programs/README.md` for the rule syntax.
- **Data** comes from clinical-data's `GET /api/v1/evaluation-inputs`, one page of patients per call.
- **Results** are kept per run; the API always reads the latest successful run. The newest 30 runs are
  kept (`rules-engine.evaluation.keep-runs`).

## API

Swagger UI: http://localhost:8081/q/swagger-ui

| Endpoint | Returns |
|---|---|
| `GET /api/v1/programs` | Programs in the folder right now, and files that fail validation |
| `GET /api/v1/programs/{id}` | One program's tiers and needs |
| `POST /api/v1/evaluations` | Starts a run (202); 409 while one is running |
| `GET /api/v1/evaluations/latest`, `/{runId}` | Run status, program file errors, counts per tier and status |
| `GET /api/v1/patients/{patientId}/programs` | A patient's programs, tiers, evidence and care needs |
| `GET /api/v1/care-needs` | Care gap list; filter by `programId`, `tierId`, `status`, `specialty` |

## Running

With the whole stack (from `services/clinical-data`):

```sh
./mvnw package -DskipTests && (cd ../rules-engine && ./mvnw package -DskipTests)
podman compose up --build
```

Needs `RULES_DB_USER` and `RULES_DB_PASSWORD` in `services/clinical-data/.env`; compose creates the
`rules_engine` database and that login on start.

Locally in dev mode (port 8081), with the stack's PostgreSQL and clinical-data running, create
`services/rules-engine/.env` with `RULES_DB_USER`, `RULES_DB_PASSWORD`, `DB_HOST=localhost` and
`DB_HOST_PORT`, then:

```sh
./mvnw quarkus:dev
```

## Tests

`./mvnw test` (needs Docker or Podman for the throwaway PostgreSQL). `CareProgramFilesTest` runs the
real files in `care-programs/` against the program specification, so an edit that changes a rule's
meaning fails there.
