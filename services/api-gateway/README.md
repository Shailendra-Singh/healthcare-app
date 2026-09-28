# api-gateway

The public entry point (backend for frontend): checks Keycloak roles, routes `/{service}/api/**` to
clinical-data, rules-engine, task-generation and the ETL, and serves one Swagger UI for all of them. No database.

## Roles

| | admin | clinical-team | scheduler |
|---|---|---|---|
| task-generation: tasks (read, update) | all types | SCHEDULING, REFERRAL | SCHEDULING |
| task-generation: generation runs (view / start) | ✔ / ✔ | ✔ / – | – / – |
| rules-engine: programs, patient programs, care needs | ✔ | ✔ (read) | – |
| rules-engine: evaluations (view / start) | ✔ / ✔ | ✔ / – | – / – |
| clinical-data: patients, encounters, reference data | ✔ | ✔ | ✔ |
| clinical-data: diagnoses, labs | ✔ | ✔ | – |
| clinical-data: ETL runs, heartbeat, evaluation inputs | ✔ | – | – |
| etl: run the ETL now (`POST /etl/api/v1/runs`, `{"force": true}` reloads unchanged files) | ✔ | – | – |

- Deny by default: `AccessPolicy` lists every grant; anything else is 403 (admin may call every API).
- Task types per role are configuration (`gateway.task-access.*`); a task of another type answers 404, as if it
  did not exist, and lists and patient task lists leave it out.
- Task changes are recorded under the logged-in user: the gateway overwrites `actor` with the token's username.

## Login

- **Browsers** (the [frontend](../../frontend), which proxies these paths on its own origin): `GET /login` runs
  the Keycloak login, keeps the session in a cookie and returns to `/`; `GET /logout` signs out and returns to `/`.
  JavaScript calls without a session get 499 instead of a redirect.
- **API clients and Swagger UI** send a bearer token issued by Keycloak for the `api-gateway` audience.
- **Behind an HTTPS reverse proxy** (TLS ending at the proxy), set `QUARKUS_OIDC_AUTHENTICATION_FORCE_REDIRECT_HTTPS_SCHEME=true`
  so Keycloak is given `https://` return addresses, and allow the public domain in Keycloak's clients.
- `GET /api/v1/me` returns the user, their roles and the task types they see.

## Swagger UI

http://localhost:8080/q/swagger-ui: the dropdown (top right) switches between the gateway and each service's API.
The docs are public; **Authorize** logs in through Keycloak (client `api-gateway-swagger`, PKCE) and every "Try it
out" call goes through the gateway with your role applied.

## Running

Start the other stacks first; each creates a network the gateway joins:

```sh
(cd ../keycloak && podman compose up -d)
(cd ../clinical-data && ./mvnw package -DskipTests && podman compose up -d --build)
(cd ../rules-engine && ./mvnw package -DskipTests && podman compose up -d --build)
(cd ../task-generation && ./mvnw package -DskipTests && podman compose up -d --build)
./mvnw package -DskipTests && podman compose up -d --build
```

Only the gateway (`localhost:8080`, `GATEWAY_PORT`) and Keycloak (`localhost:8180`, `KEYCLOAK_PORT`) are published; the services are reachable
only through the gateway (each has a `compose.dev.yaml` that publishes its ports for local development).

`.env` next to this file:

| Variable | Purpose |
|---|---|
| `GATEWAY_CLIENT_SECRET` | Secret of Keycloak's `api-gateway` client; the same value as in `services/keycloak/.env` |

Dev mode (`./mvnw quarkus:dev`, port 8080) uses Keycloak at `localhost:8180` and the services at
`localhost:8081`-`8083` and the ETL at `localhost:8084`, so start those stacks with their `compose.dev.yaml`.
Their ports are configurable in each stack's `.env` (`CLINICAL_DATA_PORT`, `ETL_PORT`, `RULES_ENGINE_PORT`,
`TASK_GENERATION_PORT`); point the gateway at them with `CLINICAL_DATA_URL`, `ETL_URL`, `RULES_ENGINE_URL` and
`TASK_GENERATION_URL`.

## Tests

`./mvnw test`: `AccessPolicyTest` covers the role matrix; `ProxyResourceTest` runs requests as each role
(`@TestSecurity`) against mocked services.
