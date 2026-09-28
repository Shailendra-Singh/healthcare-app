# Care Tasks

Loads patient data from CSV files, checks it against care programs (`care-programs/*.yaml`) and turns overdue
care needs into scheduling and referral tasks for schedulers and the clinical team.

## Setup

Needs Docker with Compose (or Podman with podman-compose).

```sh
cp env.template .env    # then replace every change-me value in .env
docker compose up -d
```

The first start builds everything from source and takes a few minutes. Then open http://localhost:3000.

| User | Sees | Password in `.env` |
|---|---|---|
| `scheduler.user` | Scheduling tasks | `SCHEDULER_USER_PASSWORD` |
| `clinical.user` | Scheduling and referral tasks, care needs | `CLINICAL_USER_PASSWORD` |
| `admin.user` | Everything, plus the ETL and evaluation pages | `ADMIN_USER_PASSWORD` |

## Data

Put `patients.csv`, `diagnoses.csv`, `labs.csv` and `encounters.csv` in `data/`. They are loaded within
`ETL_INTERVAL_SECONDS` (or at once with *Run ETL now* as admin), and tasks follow automatically.

## Addresses

| | Port in `.env` |
|---|---|
| http://localhost:3000 — the web app | `FRONTEND_PORT` |
| http://localhost:8080/q/swagger-ui — API docs | `GATEWAY_PORT` |
| http://localhost:8180/admin — Keycloak (`KEYCLOAK_ADMIN_USER`) | `KEYCLOAK_PORT` |

## Notes

- After pulling changes: `docker compose up -d --build`.
- Behind an HTTPS reverse proxy, set `QUARKUS_OIDC_AUTHENTICATION_FORCE_REDIRECT_HTTPS_SCHEME: "true"` on the
  `api-gateway` service and allow your domain in Keycloak's `api-gateway` client.
- Data lives in `.data/`; each service has its own README under `services/` and `frontend/`.
