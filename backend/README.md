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

Flyway migrations live in `src/main/resources/db/migration`.

