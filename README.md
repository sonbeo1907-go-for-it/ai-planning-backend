# AI Planning Backend

Spring Boot backend for the manual Planning Core MVP. The codebase currently
provides the shared foundation for authentication, authorization, persistence,
API errors, migrations, documentation, testing, and the planning status model.

AI generation, LMS content synchronization, notifications, reporting, and
advanced dashboards are not part of this baseline.

## Technology

- Java 17
- Spring Boot 3.5.16
- Spring Security with short-lived access JWTs and rotated refresh tokens
- Spring Data JPA
- PostgreSQL 17
- Redis 8 for revocation acceleration and refresh concurrency locks
- Flyway
- OpenAPI and Swagger UI
- JUnit 5, MockMvc, H2 for baseline integration tests
- Maven Wrapper 3.9.11

## Prerequisites

- JDK 17+
- Docker Desktop or local PostgreSQL and Redis instances

Maven does not need to be installed. Use `mvnw` or `mvnw.cmd`.

## Run locally

1. Start PostgreSQL and Redis:

   ```bash
   docker compose up -d postgres redis
   ```

2. Start the API:

   macOS/Linux:

   ```bash
   ./mvnw spring-boot:run
   ```

   Windows PowerShell:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

3. Check the service:

   - Health: <http://localhost:8080/actuator/health>
   - Swagger UI: <http://localhost:8080/swagger-ui.html>
   - OpenAPI JSON: <http://localhost:8080/v3/api-docs>

The `local` profile creates one development-only administrator when the
database is empty:

```text
email: admin@aiplanning.local
internal username: admin
password: Admin@123
```

Change these values with `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_USERNAME`
and `BOOTSTRAP_ADMIN_PASSWORD`. Local bootstrap is disabled by default outside the
`local` and `test` profiles.

## Login example

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@aiplanning.local","password":"Admin@123"}'
```

Use the returned token:

```bash
curl http://localhost:8080/api/v1/profile \
  -H "Authorization: Bearer <access-token>"
```

The login response also sets an HttpOnly `refresh_token` cookie. Logout revokes
the current login session immediately:

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer <access-token>" \
  -b "refresh_token=<refresh-token>"
```

## Environment variables

Copy `.env.example` when Docker Compose environment customization is needed.
Do not commit `.env`.

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | HMAC secret, minimum 32 characters |
| `JWT_EXPIRATION` | Access-token lifetime; default `PT15M` |
| `AUTH_SESSION_EXPIRATION` | Absolute refresh-session lifetime; default `P14D` |
| `REFRESH_LOCK_DURATION` | Redis refresh-rotation lock lifetime |
| `REFRESH_COOKIE_NAME` | HttpOnly refresh-cookie name |
| `REFRESH_COOKIE_SECURE` | Require HTTPS for the refresh cookie; use `true` outside local development |
| `REFRESH_COOKIE_SAME_SITE` | Refresh-cookie SameSite policy; default `Strict` |
| `AUTH_REDIS_ENABLED` | Enable Redis revocation keys and refresh locks |
| `REDIS_HOST` | Redis host |
| `REDIS_PORT` | Redis port |
| `REDIS_TIMEOUT` | Redis connection timeout |
| `LOGIN_MAX_ATTEMPTS` | Consecutive failed logins before temporary blocking |
| `LOGIN_BLOCK_DURATION` | Temporary login block as an ISO-8601 duration |
| `BOOTSTRAP_ADMIN_EMAIL` | Local bootstrap Admin login email |
| `BOOTSTRAP_ADMIN_USERNAME` | Local bootstrap Admin internal/display identifier |
| `CORS_ALLOWED_ORIGINS` | Frontend origin list |
| `SERVER_PORT` | HTTP port, default `8080` |

Production must provide database credentials and a strong random JWT secret.
Do not rely on values in `application-local.yml`.

## Verify changes

```bash
./mvnw clean verify
```

JaCoCo output is generated at:

```text
target/site/jacoco/index.html
```

## Project structure

```text
com.codegym.aiplanning
├── controller
│   ├── auth
│   │   └── dto
│   └── profile
│       └── dto
├── service
│   └── auth
│       └── impl
├── repository
│   └── auth
├── entity
│   ├── auth
│   └── plan
├── common
│   ├── api
│   ├── constant
│   ├── entity
│   ├── exception
└── config
```

New business modules should use the same layer-by-feature structure:

```text
controller/<feature>/
service/<feature>/impl/
repository/<feature>/
entity/<feature>/
```

For example, DailyPlan uses `controller/daily`, `service/daily`,
`repository/daily` and `entity/daily`.

## API conventions

- All public endpoints are versioned under `/api/v1` through `ApiConstant`.
- Request DTOs are validated at the API boundary.
- Successful payloads use `{ "data": ... }`.
- Errors contain `code`, `message`, `path`, `requestId`, and optional field
  violations.
- Database schema changes require a new Flyway migration.
- Timestamps are stored in UTC.
- Entities use UUID identifiers and optimistic-locking versions.
- Authorization is enforced in backend code, not only by hiding frontend
  controls.

See [CONTRIBUTING.md](CONTRIBUTING.md) and the files in [specs](specs) before
opening a pull request.
