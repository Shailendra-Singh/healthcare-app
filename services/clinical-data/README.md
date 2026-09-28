# clinical-data

Patients, diagnoses, lab results and encounters, loaded from CSV files and served as a read-only REST API
(Quarkus, Hibernate Reactive, PostgreSQL 18). The rules-engine reads it to evaluate care programs; people reach it
through the api-gateway.

## Data flow

```
data/*.csv ──► etl (Python) ──► raw.* staging tables ──► etl.process_run ──► dbo.* tables ──► REST API
```

- The **ETL** (`etl/`) checks `data/` every `ETL_INTERVAL_SECONDS` for `patients.csv`, `diagnoses.csv`, `labs.csv`
  and `encounters.csv`. When all four are present and their checksums changed, it reloads the raw tables and calls
  `etl.process_run`, which upserts into `dbo` on each record's natural key. Rows that conflict are rejected and
  listed in `etl.load_reject`.
- Admins can ask for a check now from the frontend's ETL page (`POST /etl/api/v1/runs` through the gateway;
  `{"force": true}` reloads unchanged files).
- Flyway migrations (`src/main/resources/db/migration`) create the schemas `etl`, `raw` and `dbo`.

## API

Read-only; Swagger UI at `/q/swagger-ui`. All paths are under `/api/v1`.

| Path | |
|---|---|
| `/patients`, `/patients/{id}`, `/patients/source/{sourcePatientId}` | Patients, paged |
| `/patients/{id}/diagnoses`, `/lab-results`, `/encounters`, `/encounters/upcoming` | A patient's clinical history and booked visits |
| `/languages`, `/specialties`, `/lab-tests`, `/providers`, `/condition-groups`, `/diagnosis-codes` | Reference data |
| `/evaluation-inputs` | Everything the rules-engine needs, in bulk |
| `/etl-runs`, `/etl-runs/latest`, `/etl-heartbeat` | ETL loads and the last folder check |

## Running

With the whole stack, from the repository root: `podman compose up -d --build`.

On its own (`.env` next to this file with `POSTGRES_USER`, `POSTGRES_PASSWORD` and `DB_NAME`):

```sh
./mvnw package -DskipTests
podman compose -f compose.yaml -f compose.dev.yaml up -d --build   # API on localhost:8081, ETL on 8084
```

Dev mode (live reload, Dev UI at http://localhost:8081/q/dev/) against the container database:
`podman compose -f compose.yaml -f compose.dev.yaml up -d postgres`, then `./mvnw quarkus:dev`.

| Variable | Default | |
|---|---|---|
| `DB_HOST`, `DB_HOST_PORT` | `localhost`, `5432` | The database, for `./mvnw quarkus:dev` (compose sets its own) |
| `ETL_INTERVAL_SECONDS` | `300` | How often the ETL checks the data folder |
| `ETL_FILE_SETTLE_SECONDS` | `30` | Files changed more recently are treated as still being written |
| `CLINICAL_DATA_PORT`, `ETL_PORT` | `8081`, `8084` | Ports that `compose.dev.yaml` publishes |

## Tests

`./mvnw test` runs the unit and integration tests; the integration tests start PostgreSQL through Dev Services
(Podman or Docker must be running).
