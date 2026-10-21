# fleet-ops-backend

Spring Boot backend for the fleet operations system.

## Maven

Use the Maven installation default settings and repository under:

`D:\software\apache-maven-3.9.16\`

Do not add project-local `maven.config` files that override `maven.repo.local`.

## Run Tests

```powershell
mvn test
```

## Configuration

Maven packages the single tracked `src/main/resources/application.yml`. It
contains local non-secret defaults and environment placeholders for credentials.
The following variables override its defaults:

- `FLEET_DB_URL`
- `FLEET_DB_USERNAME`
- `FLEET_DB_PASSWORD`
- `FLEET_FLYWAY_ENABLED`

For local development, use a private external configuration
file outside `src/main/resources` (for example, ignored
`.runtime/application-local.yml`) and pass it with
`--spring.config.additional-location=file:./.runtime/application-local.yml`.
Production can likewise supply `/etc/dfwl/application.yml` as an external
override; change that file without modifying the JAR. The production profile,
`FLEET_STORAGE_ROOT`, and server-side OCR variables remain available. See
`../docs/08-系统架构与部署说明_V3.0.md`.

Flyway migrations live in `src/main/resources/db/migration`.

