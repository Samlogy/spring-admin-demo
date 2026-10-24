# spring-admin-demo

Spring Boot 4.1 monolith (demo) monitored by Spring Boot Admin 4.1, with one Admin Server per environment
(`dev`, `recf`, `qua`, `prod`). Recf/qua/prod log in through ADFS / Entra ID (OIDC).

- Monolith (Admin client): repository root. Example CRUD API: `/api/products`
- Admin Server: [`admin-server/`](admin-server)
- Full documentation (architecture, env variables, auth model, prod checklist): [`docs/spring-boot-admin.md`](docs/spring-boot-admin.md)

## Run it locally (dev profile)

Prerequisites: **Java 21** and **Maven 3.9+**, or **Docker** with Compose.

### Option A: Docker Compose (simplest)

```bash
cp .env.example .env
# Fill the 4 empty passwords in .env, e.g. with: openssl rand -hex 16
docker compose up --build
```

### Option B: Maven, two terminals

```bash
# Terminal 1: Admin Server on http://localhost:8081
cd admin-server
export ADMIN_CLIENT_USER=client ADMIN_CLIENT_PASSWORD=client-secret
export DEV_ADMIN_PASSWORD=admin-secret DEV_VIEWER_PASSWORD=viewer-secret
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 2 (repository root): monolith on http://localhost:8080
export ADMIN_CLIENT_USER=client ADMIN_CLIENT_PASSWORD=client-secret
export ACTUATOR_USER=actuator ACTUATOR_PASSWORD=actuator-secret
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

On Windows PowerShell, use `$env:ADMIN_CLIENT_USER="client"` (one line per variable) instead of `export`.

The values above are throwaway local passwords. In recf/qua/prod they come from the secret manager.

## Try the CRUD API

```bash
curl -X POST localhost:8080/api/products -H 'Content-Type: application/json' -d '{"name":"Keyboard","price":49.90}'
curl -X POST localhost:8080/api/products -H 'Content-Type: application/json' -d '{"name":"Mouse","price":19.90}'
curl localhost:8080/api/products                       # list
curl localhost:8080/api/products/1                     # read
curl -X PUT localhost:8080/api/products/1 -H 'Content-Type: application/json' -d '{"name":"Mechanical keyboard","price":89.00}'
curl -X DELETE localhost:8080/api/products/2           # 204, then GET /2 -> 404
```

| Method | Path | Result |
|---|---|---|
| `GET` | `/api/products` | 200, list |
| `GET` | `/api/products/{id}` | 200, or 404 |
| `POST` | `/api/products` | 201 + `Location`, or 400 (name required, price >= 0) |
| `PUT` | `/api/products/{id}` | 200, 400 or 404 |
| `DELETE` | `/api/products/{id}` | 204, or 404 |

Data is kept in memory (no database), so it resets on restart.

## Try Spring Boot Admin

Open http://localhost:8081 and log in as `admin` / `DEV_ADMIN_PASSWORD` (or `viewer` / `DEV_VIEWER_PASSWORD`, which is read-only). The title should read **Admin DEV** with a green theme. Then click `monolith-dev`:

| Admin menu | What to try |
|---|---|
| Insights → Details | Status, version, git commit, health, JVM metrics |
| Web → HTTP Exchanges | Each CRUD call you just made, with method, status and duration |
| Web → Mappings | The `/api/products` endpoints |
| Logging → Loggers | Filter on `com.example.monolith.product` and click **DEBUG**. Then `curl localhost:8080/api/products/42` logs `Product 42 not found` (as admin; a viewer gets 403) |
| Logging → Logfile | The live log file, including those DEBUG lines |
| JVM → Threads / Heap dump, Insights → Environment | Admin only |

Stop the monolith (Ctrl+C): within a few seconds the Admin shows it OFFLINE (and sends notifications in qua/prod).

## Tests

```bash
mvn verify                       # monolith
(cd admin-server && mvn verify)  # admin server (no IdP needed)
```
