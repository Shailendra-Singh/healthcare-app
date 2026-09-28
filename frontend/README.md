# frontend

Care Tasks: the web app for schedulers and the clinical team. Plain HTML, CSS and JavaScript with
[Alpine.js](https://alpinejs.dev) and [Pico CSS](https://picocss.com) (vendored in `public/vendor`, no build
step, no npm), served by nginx.

- **Login page**: *Sign in* goes to the organisation's Keycloak page; the app never sees passwords.
- **Worklist**: the tasks the user's role may see, filtered by task type, specialty, status and "assigned to me";
  start, complete, cancel or reopen a task.
  - Scheduler: scheduling tasks only.
  - Clinical team: scheduling and referral tasks.
  - Admin: every task.
- **Patients**: the patient list and search; a patient's care programs and needs (clinical team and admin) and
  their tasks.
- **ETL** (admins): when the ETL last checked the data folder, the latest load with its files, rows and
  rejected rows, and the load history. *Run ETL now* checks the folder immediately; *Reload even if unchanged*
  loads the files again, which also re-runs the evaluation and task generation downstream.
- **Evaluations** (admins): the latest evaluation's patients, care needs by status (met, scheduled, overdue) and
  patients per program and tier, with the run history. *Run evaluation now* starts one and follows it to the end.
- **Swagger UI** link in the user menu and on the login page.
- **Phones**: tables become cards, the tabs move to a bar at the bottom, and the patient detail opens full
  screen. Light and dark themes follow the device. It can be added to the home screen (web app manifest).

## How it fits together

nginx serves `public/` and passes every other path to the api-gateway, so the page and the API share one origin:

```
browser ──► frontend (nginx, :3000) ──► /index.html, /app.js, /app.css, /vendor/*
                     └──────────────► api-gateway: /login, /logout, /api/v1/me, /{service}/api/**, /q/swagger-ui
```

- Login is the gateway's (backend for frontend): `/login` runs the Keycloak login and keeps the session in an
  HTTP-only cookie. The JavaScript never handles tokens.
- The gateway decides what each role sees; the app shows whatever the gateway returns (a 403 on care needs is
  shown as "available to the clinical team"). A call without a session gets 499 and the app shows the login page.
- Keycloak's `api-gateway` and `api-gateway-swagger` clients allow `http://localhost:${FRONTEND_PORT}` as a
  redirect URI.

## Running

With the whole stack, from the repository root: `podman compose up -d --build`, then open http://localhost:3000.

To use another port, set `FRONTEND_PORT` in the root `.env`. Keycloak reads it when it first creates the realm; if
the realm already exists, see *Changing ports later* in [services/keycloak/README.md](../services/keycloak/README.md).

On its own, against a gateway running on the host (e.g. `./mvnw quarkus:dev` in `services/api-gateway`):

```sh
podman build -t healthcare/frontend .
podman run --rm -p 3000:8080 -e GATEWAY_URL=http://host.containers.internal:8080 healthcare/frontend
```

| Variable | Purpose | Default |
|---|---|---|
| `GATEWAY_URL` | Where nginx sends everything that isn't a file in `public/` | `http://api-gateway:8080` |

## Files

| | |
|---|---|
| `public/index.html` | The page: login, worklist, patients, and the admin ETL and evaluations views |
| `public/app.js` | The Alpine.js component: session check, API calls, filters, task actions, running the ETL and evaluations |
| `public/app.css` | The app's styles on top of Pico (badges, cards on phones, tab bar, patient sheet) |
| `public/favicon.svg`, `favicon.ico`, `icons/` | App icon: browser tab, iOS home screen, Android (manifest) |
| `public/manifest.json` | Web app manifest: name, colours and icons for "Add to home screen" |
| `public/vendor/` | Alpine.js 3.17.4 and Pico CSS 2.1.1 (see its README for sources and integrity hashes) |
| `nginx/default.conf.template` | Static files, proxy to the gateway, security headers |
