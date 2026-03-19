# AGENTS Guide

This document serves as a guide for AI agents contributing to the development of the Event Manager application. It outlines the current state of the project, architectural decisions, configuration conventions, developer workflows, and important notes for making changes.

## Project Snapshot
- Stack: Spring Boot 4.0.3, Java 21, Maven Wrapper, servlet MVC (`spring-boot-starter-webmvc`).
- Persistence baseline is relational: JPA + PostgreSQL runtime driver (`pom.xml`).
- Observability is expected early: Actuator is included and Docker healthcheck calls `/actuator/health`.
- Current code is scaffold-only: app bootstrap class and one `@SpringBootTest` context test.

## Structure Source
```
src/main/java/com/tak/fanmeeting/
├── config/              # Cấu hình Bean tổng (RedisConfig, SecurityConfig, RabbitMQConfig)
├── core/
│   ├── exception/       # GlobalExceptionHandler (@RestControllerAdvice)
│   └── security/        # JwtAuthenticationFilter
├── domain/              # CHIA THEO TỪNG MODULE
│   ├── auth/
│   │   ├── dto/         # LoginRequest, TokenResponse
│   │   ├── controller/  # AuthController
│   │   └── service/     # AuthService
│   ├── event/
│   │   ├── entity/      # Event (Map với bảng events)
│   │   ├── dto/         # EventCreateReq, EventRes
│   │   ├── repository/  # EventRepository (extends JpaRepository)
│   │   ├── controller/  # EventController
│   │   └── service/     # EventService
│   ├── booking/         # Tương tự...
│   └── ticket/          # Tương tự...
└── worker/              # Các background task
├── BookingConsumer  # @RabbitListener đọc queue
└── ExpirationTask   # @Scheduled chạy ngầm dọn vé hết hạn
```

## Architecture and Boundaries
- Root package is `com.kietta.eventmanager`; Spring component scanning starts here (`EventmanagerApplication.java`).
- No domain modules exist yet; add new packages under `src/main/java/com/kietta/eventmanager/` so they are auto-scanned.
- Data flow implied by dependencies: HTTP (WebMVC) -> service/repository layer (to be added) -> JPA -> PostgreSQL.
- `src/main/resources/templates/` and `src/main/resources/static/` exist, so MVC can support server-rendered pages and static assets in addition to JSON APIs.

## Configuration and Integrations
- Runtime config is environment-driven; `.env.example` documents required variables:
  - `SPRING_PROFILES_ACTIVE`
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`
- `docker-compose.yml` injects `.env` into the app container (`env_file`), so local container runs rely on env vars rather than hardcoded `application.properties`.
- `application.properties` currently only sets `spring.application.name=eventmanager`; keep secrets and environment-specific DB settings out of this file.

## Developer Workflows
- Run tests (baseline check):
  - `./mvnw --no-transfer-progress test`
- Run app locally from source:
  - `./mvnw spring-boot:run`
- Build jar:
  - `./mvnw clean package`
- Containerized run:
  - `docker compose up --build`

## Container Build Conventions
- `Dockerfile` uses a 3-stage build (`deps` -> `builder` -> `runtime`) and Spring Boot layered jar extraction for cache-efficient rebuilds.
- Maven build in image uses `-DskipTests`; do not treat Docker image build as test validation.
- Runtime image is non-root (`appuser`) and includes JVM container tuning via `JAVA_OPTS`.
- Health status in container is tied to Actuator availability; preserve `/actuator/health` behavior when adding security.

## AI Agent Notes for Changes
- Prefer incremental PR-sized changes; this repository is still at skeleton stage.
- When adding features, include the package structure and wiring (controller/service/repository/entity) in one coherent slice so the app remains runnable.
- If adding DB-dependent tests, provide profile-specific test config (or test containers) so `./mvnw test` remains reliable outside Docker.
- Reference key files when changing behavior: `pom.xml`, `Dockerfile`, `docker-compose.yml`, `src/main/resources/application.properties`.

