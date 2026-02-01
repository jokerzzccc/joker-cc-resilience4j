# Repository Guidelines

## Project Structure & Module Organization
This is a Maven-based Spring Boot/WebFlux learning project for Resilience4j Reactor.
Key paths:
- `src/main/java/com/joker/resilience4j/` for application code (`Application.java` plus intended subpackages like `config/`, `controller/`, `service/`, `model/`).
- `src/main/resources/application.yml` for runtime config and management endpoints.
- `src/test/java/com/joker/resilience4j/` for tests (currently empty).
- `.analysis/` for architecture, plan, and progress notes.
- `docs/` for tutorial-style documentation; `examples/` for sample code.

## Build, Test, and Development Commands
- `mvn clean install` builds the project and runs tests.
- `mvn test` runs the unit test suite only.
- `mvn spring-boot:run` starts the local app (default port `8080`).
Example endpoints (after running): `GET /api/circuit-breaker/test`, `GET /api/rate-limiter/test`, `GET /actuator/health`.

## Coding Style & Naming Conventions
- Java 21 is required (`--enable-preview` is enabled in the Maven compiler plugin).
- Follow standard Java style: 4-space indentation, braces on the same line.
- Package names are lower-case; classes use PascalCase; methods/fields use camelCase.
- No formatter or linter is configured; keep imports tidy and avoid unused code.

## Testing Guidelines
- Use JUnit 5 via `spring-boot-starter-test`, plus Reactor Test and Resilience4j Test helpers.
- Place tests under `src/test/java/com/joker/resilience4j/` and name classes `*Test`.
- No explicit coverage threshold is configured; focus on behavior and edge cases.

## Commit & Pull Request Guidelines
- Git history follows Conventional Commits (e.g., `feat: ...`); prefer `feat`, `fix`, `docs`, `test`, `chore`.
- PRs should include a short summary, local run steps, and any API calls used for verification.

## Agent/Process Notes
- Read `CLAUDE.md` and `.analysis/` docs before making staged changes.
- Docker commands in docs are for reference only; do not execute them.
- If you follow the staged plan, update `.analysis/implementation_progress.md` after each stage.
