# keycloak

The identity provider for the api-gateway: Keycloak 26 with its own PostgreSQL 18 (`keycloak-db`, data in
`.data/`) on the private `keycloak-network`. Only Keycloak also joins `keycloak-api-network`, for the gateway.

```sh
podman compose up -d        # or: docker compose up -d
```

- Login pages and admin console: http://localhost:8180 (admin console at `/admin`, with `KEYCLOAK_ADMIN_USER`)
- Realm `healthcare`, imported from `realm/healthcare-realm.json` on the **first** start only. To change it
  afterwards, use the admin console, or delete `.data/` (`podman unshare rm -rf .data` under rootless Podman)
  to re-import, which also deletes users created since.

## Realm

| | |
|---|---|
| Roles | `admin` (sees everything), `scheduler` (scheduling tasks), `clinical-team` (scheduling and referral tasks) |
| Client `api-gateway` | Confidential; the gateway's browser login (session cookie) and bearer token checks |
| Client `api-gateway-swagger` | Public with PKCE; the Authorize button in the gateway's Swagger UI |
| Test users | `admin.user`, `scheduler.user`, `clinical.user`, one per role |

Access tokens carry the roles in `realm_access.roles` and `api-gateway` as audience.

## .env

| Variable | Purpose |
|---|---|
| `KEYCLOAK_DB_USER`, `KEYCLOAK_DB_PASSWORD` | Keycloak's database login, created on first start |
| `KEYCLOAK_ADMIN_USER`, `KEYCLOAK_ADMIN_PASSWORD` | Keycloak admin console login (not an app role) |
| `GATEWAY_CLIENT_SECRET` | Secret of the `api-gateway` client; the same value goes in `services/api-gateway/.env` |
| `ADMIN_USER_PASSWORD`, `SCHEDULER_USER_PASSWORD`, `CLINICAL_USER_PASSWORD` | Passwords of the three test users |

`start-dev` serves plain HTTP for local use; production needs `start` with TLS and a real hostname.
