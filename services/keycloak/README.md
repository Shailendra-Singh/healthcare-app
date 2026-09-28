# keycloak

The identity provider for the api-gateway: Keycloak 26 with its own PostgreSQL 18 (`keycloak-db`, data in
`.data/`) on the private `keycloak-network`. Only Keycloak also joins `keycloak-api-network`, for the gateway.

```sh
podman compose up -d        # or: docker compose up -d
```

- Login pages and admin console: http://localhost:8180 (`KEYCLOAK_PORT`; admin console at `/admin`, with
  `KEYCLOAK_ADMIN_USER`)
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

## Login theme

`themes/care-tasks` gives the login pages the Care Tasks look (logo, colours, font, light and dark mode). It
extends Keycloak's own `keycloak.v2` theme and only adds a stylesheet and images, so every page and flow stays
Keycloak's. Compose mounts it into `/opt/keycloak/themes`, and the realm's `loginTheme` selects it. `start-dev`
does not cache themes: edit `resources/css/care-tasks.css` and reload the page.

## .env

| Variable | Purpose |
|---|---|
| `KEYCLOAK_DB_USER`, `KEYCLOAK_DB_PASSWORD` | Keycloak's database login, created on first start |
| `KEYCLOAK_ADMIN_USER`, `KEYCLOAK_ADMIN_PASSWORD` | Keycloak admin console login (not an app role) |
| `GATEWAY_CLIENT_SECRET` | Secret of the `api-gateway` client; the same value goes in `services/api-gateway/.env` |
| `ADMIN_USER_PASSWORD`, `SCHEDULER_USER_PASSWORD`, `CLINICAL_USER_PASSWORD` | Passwords of the three test users |
| `KEYCLOAK_PORT` | Keycloak's port on this machine (default 8180): login pages and admin console |
| `FRONTEND_PORT`, `GATEWAY_PORT` | The frontend's and gateway's ports (defaults 3000, 8080); the clients' login and logout return addresses use them |

### Changing ports later

The realm reads `FRONTEND_PORT` and `GATEWAY_PORT` only when it is first created. After changing either in an
existing setup, restart Keycloak with the new values and update the two clients (from the repository root):

```sh
podman compose up -d --force-recreate keycloak
podman compose exec -T keycloak sh < services/keycloak/sync-client-urls.sh
```

`KEYCLOAK_PORT` needs only the restart (of Keycloak and the api-gateway).

`start-dev` serves plain HTTP for local use; production needs `start` with TLS and a real hostname.
