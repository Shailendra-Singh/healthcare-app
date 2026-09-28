# task-generation

Turns the rules-engine's care needs into **scheduling** and **referral** tasks, stores them in its own
`task_generation` database, and lets people work them through the API.

- **When:** every `TASK_POLL_EVERY` (default 5 minutes) it checks the rules-engine for a newer successful
  evaluation and reconciles against it; `POST /api/v1/generation-runs` reconciles immediately.
- **Which task:** decided per need by `TaskPlanner`, from the program's `tasks:` policy in `care-programs/`:

  | Care need | Task |
  |---|---|
  | Due, specialty seen before (last visit older than the cadence) | the policy's `pastCadence` (scheduling) |
  | Due, specialty never seen | the policy's `neverSeen` (referral for Diabetes Management, none for Primary Care Wellness) |
  | Within cadence, or an appointment is booked | none; an open task is closed (RESOLVED) with the reason |

- **One task per patient, program, specialty and type.** Re-running on the same evaluation changes nothing;
  a task still needed is updated (tier, cadence, due date, priority), keeping its status and assignee.
- **People's decisions stand.** A task someone completed or cancelled is not recreated while the patient's last
  visit for that need stays the same.
- **Nothing is deleted.** Closed tasks stay, with who closed them, why, and the full history.

## API

Swagger UI: http://localhost:8083/q/swagger-ui

| Endpoint | Does |
|---|---|
| `GET /api/v1/tasks` | Work list, most urgent first; `status` (ACTIVE by default, ALL or one status), `taskType`, `programId`, `specialty`, `assignee` |
| `GET /api/v1/tasks/{taskId}` | A task with its history |
| `PATCH /api/v1/tasks/{taskId}` | `{"actor": "...", "status": "...", "assignee": "...", "reason": "..."}`: OPEN to IN_PROGRESS, COMPLETED or CANCELLED; IN_PROGRESS to OPEN, COMPLETED or CANCELLED |
| `GET /api/v1/patients/{patientId}/tasks` | A patient's tasks, open and closed |
| `GET /api/v1/task-types` | SCHEDULING, REFERRAL |
| `POST /api/v1/generation-runs`, `GET .../latest`, `GET .../{runId}` | Reconcile now; run results |

Tasks hold only the patient id; patient details come from clinical-data.

## Running

task-generation has its own stack (`compose.yaml` here): the app and a dedicated PostgreSQL
(`task-generation-db`, data in `.data/`) on the private `task-generation-network`. Only the app also joins
`rules-engine-api-network` to call the rules-engine API, so start clinical-data and rules-engine first:

```sh
./mvnw package -DskipTests
podman compose up -d --build        # or: docker compose up -d --build
```

| | From your machine | Inside the networks |
|---|---|---|
| task-generation | `localhost:8083` | `task-generation:8080` |
| task-generation-db | `localhost:5434` | `task-generation-db:5432` (task-generation-network only) |
| rules-engine API | `localhost:8082` | `rules-engine:8080` (rules-engine-api-network) |

`.env` next to this file:

| Variable | Required | Purpose |
|---|---|---|
| `TASK_DB_USER`, `TASK_DB_PASSWORD` | Yes | Login of the task-generation database, created on first start |
| `DB_HOST` | No (`localhost`) | Database host for `./mvnw quarkus:dev`; compose uses `task-generation-db` |
| `DB_HOST_PORT` | No (5434) | Host port of the task-generation database |
| `TASK_DB_NAME` | No (`task_generation`) | Database name |
| `TASK_POLL_EVERY` | No (`5m`) | How often to check the rules-engine for a new evaluation |
| `TZ` | No (UTC) | Container time zone |

Locally in dev mode (port 8083, calling the rules-engine at `localhost:8082`), start just the database with
`podman compose up -d task-generation-db`, then `./mvnw quarkus:dev`.

## Tests

`./mvnw test` (needs Docker or Podman for the throwaway PostgreSQL). `TaskPlannerTest` covers the task
generation criteria need by need; `TaskGenerationServiceTest` covers reconciliation over successive evaluations.
