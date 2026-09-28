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

Swagger UI: http://localhost:8082/q/swagger-ui

| Endpoint | Returns |
|---|---|
| `GET /api/v1/programs` | Programs in the folder right now, and files that fail validation |
| `GET /api/v1/programs/{id}` | One program's tiers and needs |
| `POST /api/v1/evaluations` | Starts a run (202); 409 while one is running |
| `GET /api/v1/evaluations/latest`, `/{runId}` | Run status, program file errors, counts per tier and status |
| `GET /api/v1/patients/{patientId}/programs` | A patient's programs, tiers, evidence and care needs |
| `GET /api/v1/care-needs` | Care gap list; filter by `programId`, `tierId`, `status`, `specialty` |

## Running

rules-engine has its own stack (`compose.yaml` here): the app and a dedicated PostgreSQL
(`rules-engine-db`, data in `.data/`) on the private `rules-engine-network`. Only the app joins the API
networks: `clinical-data-api-network` to call clinical-data (so start that stack first) and
`rules-engine-api-network`, which task-generation joins to call this API:

```sh
(cd ../clinical-data && ./mvnw package -DskipTests && podman compose up -d --build)
./mvnw package -DskipTests
podman compose up -d --build        # or: docker compose up -d --build
```

By default nothing is published on your machine: the API is reached through the api-gateway
(`http://localhost:8080/rules-engine/api/v1/...`, with a Keycloak login). For local development, add
`compose.dev.yaml`, which publishes the ports below: `podman compose -f compose.yaml -f compose.dev.yaml up -d`.

| | With compose.dev.yaml | Inside the networks |
|---|---|---|
| rules-engine | `localhost:8082` | `rules-engine:8080` (rules-engine-api-network) |
| rules-engine-db | `localhost:5433` | `rules-engine-db:5432` (rules-engine-network only) |
| clinical-data API | `localhost:8081` | `clinical-data:8080` (clinical-data-api-network) |

`.env` next to this file:

| Variable | Required | Purpose |
|---|---|---|
| `RULES_DB_USER`, `RULES_DB_PASSWORD` | Yes | Login of the rules-engine database, created on first start |
| `DB_HOST` | Dev mode | `localhost` when running `./mvnw quarkus:dev`; compose uses `rules-engine-db` |
| `DB_HOST_PORT` | No (5433) | Host port of the rules-engine database |
| `RULES_DB_NAME` | No (`rules_engine`) | Database name |
| `EVALUATION_CRON` | No (`0 0 6 * * ?`) | Daily run time (Quartz cron) |
| `TZ` | No (UTC) | Time zone that decides "today" in the container |

The login and database are created only when `.data/` is empty; to change them later, remove `.data/`
(`podman unshare rm -rf .data` under rootless Podman), which deletes the stored results.

Locally in dev mode (port 8082, calling clinical-data at `localhost:8081`, so start clinical-data with its
`compose.dev.yaml` too), start just the database with
`podman compose -f compose.yaml -f compose.dev.yaml up -d rules-engine-db`, then:

```sh
./mvnw quarkus:dev
```

## Tests

`./mvnw test` (needs Docker or Podman for the throwaway PostgreSQL). `CareProgramFilesTest` runs the
real files in `care-programs/` against the program specification, so an edit that changes a rule's
meaning fails there.
