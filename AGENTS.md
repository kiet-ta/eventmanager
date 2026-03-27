# AGENTS Guide

## Stack and Runtime
- Java 21 + Spring Boot 4.0.3 with servlet MVC (`spring-boot-starter-webmvc`) in `pom.xml`.
- Main package root is `com.kietta.eventmanager`; keep new code under this package for component scan.
- Data services: PostgreSQL (`spring.datasource.*`) + Flyway migrations (`src/main/resources/db/migration`) + Redis (`spring.data.redis.*`).
- Mail and JWT are first-class auth dependencies (`spring-boot-starter-mail`, `jjwt-*`).

## Current Architecture (What Exists)
- Auth flow is passwordless OTP in `src/main/java/com/kietta/eventmanager/domain/auth`:
  - `AuthController`: `/api/v1/auth/send-otp`, `/verify-otp`, `/complete-register`.
  - `AuthService`: orchestrates reCAPTCHA -> OTP -> identity lookup -> JWT/register-token.
  - `OtpService`: Redis keys `otp:{email}` and `otp_fails:{email}` with TTL/lock policy.
  - `JwtService`: issues access tokens and short-lived register tokens.
- Persistence model is entity-first with JPA annotations under `domain/*/entity` and repositories under `domain/*/repository`.
- Security is centralized in `core/security/SecurityConfig.java` (CORS + open `/api/v1/auth/**`, other APIs authenticated).

## Data and Migration Conventions
- Treat Flyway as source of schema truth; add incremental `V{n}__*.sql` files, never rewrite applied migrations.
- `user_identities` stores provider mapping (`provider`, `provider_id`, `is_verified`) and no password column.
- Keep naming consistent with SQL snake_case (`user_id`, `created_at`, etc.) when using `@JoinColumn`.

## Config Conventions
- Security settings live under `app.security.*` in `src/main/resources/application.yml`:
  - `app.security.jwt.*`, `app.security.otp.*`, `app.security.recaptcha.*`.
- Environment variables are required for secrets (`JWT_SECRET_KEY`, reCAPTCHA, mail, datasource, Redis password).
- Avoid hardcoding secrets into committed YAML/properties.

## Developer Workflows
- Local test run:
  - `./mvnw --no-transfer-progress test`
- Local app run:
  - `./mvnw spring-boot:run`
- Container run with Redis sidecar:
  - `docker compose up --build`
- Docker image build skips tests (`-DskipTests` in `Dockerfile`), so always run tests separately.

## Project-Specific Patterns
- Controller methods return `ResponseEntity<?>` with map-based error payloads (`{"error": "..."}`) for expected failures.
- OTP verify semantics:
  - existing identity -> `200` + access token,
  - new identity -> `202` + register token,
  - too many wrong OTP attempts -> `429`.
- Email normalization (`trim + lowercase`) is applied before identity lookup and Redis key operations.

## Safe Change Rules for Agents
- Prefer vertical slices (DTO + controller + service + repository + migration + tests together).
- If auth contract changes, update `AuthControllerTest` and verify status-code behavior for `200/202/429`.
- Keep CORS in sync with frontend origin (currently `http://localhost:3000` in `SecurityConfig`).

