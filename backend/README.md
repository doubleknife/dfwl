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

Database settings are environment-driven:

- `FLEET_DB_URL`
- `FLEET_DB_USERNAME`
- `FLEET_DB_PASSWORD`
- `FLEET_FLYWAY_ENABLED`

The tracked `application*.yml` files are packaged into the JAR and contain no
local credentials. For local development, use a private external configuration
file outside `src/main/resources` (for example, ignored
`.runtime/application-local.yml`) and pass it with
`--spring.config.additional-location=file:./.runtime/application-local.yml`.
Production uses `SPRING_PROFILES_ACTIVE=prod`, `FLEET_STORAGE_ROOT`, and the
server-side OCR environment variables. See
`../docs/11-R8-1-部署配置与敏感信息.md`.

Flyway migrations live in `src/main/resources/db/migration`.

