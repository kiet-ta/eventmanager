# AGENTS Guide – Backend Source Code Analysis

## I. Stack and Runtime

### Core Framework
- **Java 21** + **Spring Boot 4.0.3** with servlet MVC (`spring-boot-starter-webmvc`)
- Main package root: `com.kietta.eventmanager` (for Spring component scanning)
- Build tool: Maven (`pom.xml` in root)

### Key Dependencies
| Dependency | Purpose | Version |
|---|---|---|
| `spring-boot-starter-security` | Authentication & authorization | 4.0.3 |
| `jjwt-*` (api, impl, jackson) | JWT token generation & validation | 0.12.5 |
| `spring-boot-starter-mail` | OTP email delivery | 4.0.3 |
| `spring-boot-starter-data-redis` | OTP state + token family tracking | 4.0.3 |
| `spring-boot-starter-data-jpa` | ORM for SQL entities | 4.0.3 |
| `flyway-core` + `flyway-database-postgresql` | Database versioning | Latest |
| `postgresql` | Primary database driver | Runtime scope |
| `lombok` | Boilerplate reduction | Optional |

### Data Infrastructure
- **PostgreSQL**: Primary relational database (URL from `SPRING_DATASOURCE_URL` env var)
- **Redis**: Session/OTP/token state (`SPRING_DATA_REDIS_HOST`, `REDIS_PASSWORD`)
- **Flyway**: Schema migrations in `src/main/resources/db/migration/`
  - `V1__init_database.sql`: Initial schema (users, user_identities, events, venues, bookings, tickets)
  - `V2__passwordless_auth_cleanup.sql`: Removed password columns after passwordless refactor
  - Applied baseline: `flyway.baseline-on-migrate: true` for existing databases
- **Mail Service**: SMTP configured for Gmail (`smtp.gmail.com:587`)

---

## II. Current Architecture – Core Domains

### 2.1 Authentication Domain (`domain/auth`)

**Flow**: Passwordless OTP-based authentication with token-chain rotation.

#### Endpoints (All Public, No Auth Required)
```
POST /api/v1/auth/send-otp
├─ Input:  { email, recaptchaToken }
├─ Process: reCAPTCHA verify → Generate OTP → Email send
└─ Output: 200 { message: "Ma OTP da duoc gui" }

POST /api/v1/auth/verify-otp  (alias: /login)
├─ Input:  { email, otp }
├─ Process: Lock check → OTP verification → Identity lookup
├─ Output (Existing User): 200 { accessToken, refreshToken (in cookie), tokenType: "Bearer" }
├─ Output (New User): 202 { registerToken, status: "REGISTRATION_REQUIRED" }
└─ Output (Locked): 429 { error: "Khong tim thay refresh token..." }

POST /api/v1/auth/complete-register
├─ Input:  { registerToken, firstName, lastName, identityNumber, identityType }
├─ Process: Validate registerToken → Create User + UserIdentity → Issue JWT
└─ Output: 200 { accessToken, tokenType: "Bearer" } (refreshToken in cookie)

POST /api/v1/auth/refresh
├─ Input:  Cookie: refreshToken (HttpOnly)
├─ Process: Extract family ID → Verify JTI → Rotate tokens → Update family
└─ Output: 200 { accessToken } (new refreshToken in cookie)

POST /api/v1/auth/logout
├─ Input:  Cookie: refreshToken (HttpOnly)
├─ Process: Extract familyId → Revoke entire token family
└─ Output: 200 { message: "Logged out" }
```

#### Key Services
- **`AuthService`** (`service/AuthService.java`):
  - Orchestrates OTP flow, identity resolution, registration
  - Manages JWT issuance & refresh token rotation with theft detection
  - Email normalization: `trim().toLowerCase(Locale.ROOT)`
  - Throws `ResponseStatusException` for auth failures

- **`OtpService`** (`service/OtpService.java`):
  - Redis keys: `otp:{email}` (stores OTP + expiry), `otp_fails:{email}` (failure counter)
  - 5-minute TTL, 5 failed attempts before lock
  - Clears state after successful verification

- **`JwtService`** (`service/JwtService.java`):
  - Access tokens: 15 minutes (`app.security.jwt.access-token-expiration-ms: 900000`)
  - Refresh tokens: 7 days (`app.security.jwt.refresh-token-expiration-ms: 604800000`)
  - Register tokens: 15 minutes (temporary, for new user registration)
  - Includes `familyId` + `jti` in refresh token for rotation tracking

- **`RefreshTokenService`** (`service/RefreshTokenService.java`):
  - Maintains JTI family chains in Redis for token rotation
  - Detects stolen tokens: if JTI doesn't match family, revokes entire family
  - Key: `refresh_token_family:{familyId}`

- **`RecaptchaService`** (`service/RecaptchaService.java`):
  - Calls Google reCAPTCHA API (`https://www.google.com/recaptcha/api/siteverify`)
  - Can be disabled via `APP_SECURITY_RECAPTCHA_ENABLED=false`
  - Returns 400 if verification fails

- **`NotificationService`** (`service/NotificationService.java`):
  - Sends OTP codes via email (Spring Mail)
  - Recipient: normalized email address

#### DTOs
- `SendOtpRequest`: `{ email: String, recaptchaToken: String }`
- `VerifyOtpRequest`: `{ email: String, otp: String }`
- `VerifyOtpResponse`: `{ accessToken, refreshToken?, message, status, tokenType }`
- `CompleteRegisterRequest`: `{ registerToken, firstName, lastName, identityNumber, identityType }`
- `AuthResponse`: `{ accessToken, refreshToken?, tokenType }`

---

### 2.2 User Domain (`domain/user`)

#### Entity: User (`entity/User.java`)
```java
@Entity @Table("users")
├─ id: UUID (Primary Key, auto-generated)
├─ firstName: String (50 chars, required)
├─ lastName: String (50 chars, required)
├─ identityNumber: String (unique, required) // CCCD or Passport
├─ identityType: Enum { CCCD, PASSPORT } // Default: CCCD
├─ phoneNumber: String (optional)
├─ role: Enum { USER, ADMIN } // Default: USER
├─ status: Enum { ACTIVE, INACTIVE, SUSPENDED } // Default: ACTIVE
├─ createdAt: Instant (auto-set, immutable)
└─ updatedAt: Instant (auto-set, mutable)
```

#### Repository
- `UserRepository extends JpaRepository<User, UUID>`
- Custom queries (if any) not yet exposed in public API

#### Service
- `UserService`: User lookup, creation, profile updates
- Validates identity number uniqueness

---

### 2.3 User Identities Domain (`domain/user_identities`)

#### Entity: UserIdentity (`entity/UserIdentity.java`)
```java
@Entity @Table("user_identities")
├─ id: UUID (Primary Key, auto-generated)
├─ user: User (ManyToOne, FK: user_id)
├─ provider: String (e.g., "LOCAL", "GOOGLE", "FACEBOOK")
├─ providerId: String (unique, e.g., email for LOCAL)
├─ isVerified: Boolean (default: false)
├─ createdAt: Instant (immutable)
└─ updatedAt: Instant (mutable)
```

#### Purpose
- Maps external identity providers to Users
- `LOCAL` provider uses email as `providerId`
- Supports future OAuth integrations (GOOGLE, FACEBOOK)

#### Repository
- `UserIdentityRepository extends JpaRepository<UserIdentity, UUID>`
- Query: `findByProviderIgnoreCaseAndProviderIdIgnoreCase(provider, providerId)` → case-insensitive lookup

---

### 2.4 Event Domain (`domain/event`)

#### Entity: Event (`entity/Event.java`)
```java
@Entity @Table("events")
├─ id: UUID (auto-generated)
├─ title: String (required)
├─ description: String (TEXT column, optional)
├─ venue: Venue (ManyToOne, FK: venue_id)
├─ organizer: User (ManyToOne, FK: organizer_id)
├─ eventDate: Instant (required)
├─ openSaleTime: Instant (required, when tickets go on sale)
├─ status: Enum { DRAFT, PUBLISHED, ONGOING, FINISHED, CANCELLED }
├─ createdAt: Instant (immutable)
└─ updatedAt: Instant (mutable)
```

#### Status Enum
- `DRAFT`: Event created, not published
- `PUBLISHED`: Open for ticket sales
- `ONGOING`: Event is currently happening
- `FINISHED`: Event completed
- `CANCELLED`: Event cancelled

#### Structure
- Controller, service, repository pattern (not yet detailed in public APIs)

---

### 2.5 Booking Domain (`domain/booking`)

#### Purpose
- Tracks user ticket reservations for events
- Manages booking lifecycle (pending, confirmed, cancelled, etc.)

#### Entity Structure (assumed)
- `Booking`: User + Event + quantity + timestamp
- `BookingStatus`: PENDING, CONFIRMED, CANCELLED, EXPIRED

---

### 2.6 Ticket Domain (`domain/ticket`)

#### Purpose
- Manages individual ticket issuance and redemption
- Tracks ticket status per event

#### Entity Structure (assumed)
- `Ticket`: Booking reference, unique code, QR code, status, seat/section info

---

### 2.7 Venue Domain (`domain/venue`)

#### Entity: Venue (`entity/Venue.java`)
- Location for events
- Capacity, address, contact info
- Referenced by Event (ManyToOne)

---

## III. Security Architecture

### 3.1 Security Configuration (`core/security/SecurityConfig.java`)

#### Authentication
- **Stateless**: `SessionCreationPolicy.STATELESS`
- **No CSRF**: `csrf().disable()`
- **No Basic Auth**: `httpBasic().disable()`
- **No Form Login**: `formLogin().disable()`

#### CORS Policy
```yaml
Allowed Origins: ${APP_SECURITY_CORS_ALLOWED_ORIGINS}
# Default: http://localhost:3000, http://127.0.0.1:3000
Allowed Headers: * (all)
Allowed Methods: * (all, including OPTIONS)
Credentials: true
Max Age: 3600 seconds
```

#### Authorization Rules
```
✅ Public (No Auth Required)
├─ OPTIONS /** (preflight)
├─ POST /api/v1/auth/send-otp
├─ POST /api/v1/auth/login
├─ POST /api/v1/auth/verify-otp
├─ POST /api/v1/auth/complete-register
├─ POST /api/v1/auth/refresh
├─ POST /api/v1/auth/logout
├─ GET /error

❌ Protected (Requires Valid Access Token)
└─ All other endpoints (anyRequest().authenticated())
```

#### JWT Filter
- **Name**: `JwtAuthenticationFilter` (`core/security/middleware/JwtAuthenticationFilter.java`)
- **Position**: Before `UsernamePasswordAuthenticationFilter`
- **Logic**:
  1. Extract JWT from `Authorization: Bearer <token>` header
  2. Validate signature & expiration
  3. Extract user claims (id, email, role)
  4. Set `SecurityContext` with user principal
  5. Pass to next filter if valid; reject if invalid

#### Password Encoder
- **BCryptPasswordEncoder**: Configured but not actively used (passwordless auth)
- Available for future OAuth integrations

### 3.2 Cookie Management (`core/security/CookieUtils.java`)

#### Refresh Token Cookie
- **Name**: `refreshToken`
- **HttpOnly**: true (prevents JS access)
- **Secure**: true (HTTPS only in production)
- **SameSite**: Strict (CSRF protection)
- **Path**: `/api/v1/auth` (only sent to auth endpoints)
- **Domain**: `localhost` (configurable)
- **Max Age**: 7 days

---

## IV. Data Schema (Flyway Migrations)

### V1__init_database.sql
```sql
-- users
CREATE TABLE users (
  id UUID PRIMARY KEY,
  first_name VARCHAR(50) NOT NULL,
  last_name VARCHAR(50) NOT NULL,
  identity_number VARCHAR(50) UNIQUE NOT NULL,
  identity_type VARCHAR(20) DEFAULT 'CCCD',
  phone_number VARCHAR(20),
  role VARCHAR(20) DEFAULT 'USER',
  status VARCHAR(20) DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP
);

-- user_identities (multi-provider mapping)
CREATE TABLE user_identities (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  provider VARCHAR(50) NOT NULL,
  provider_id VARCHAR(255) UNIQUE NOT NULL,
  is_verified BOOLEAN DEFAULT false,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP
);

-- events
CREATE TABLE events (
  id UUID PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  venue_id UUID REFERENCES venues(id),
  organizer_id UUID REFERENCES users(id),
  event_date TIMESTAMP NOT NULL,
  open_sale_time TIMESTAMP NOT NULL,
  status VARCHAR(20),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

-- venues
CREATE TABLE venues (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  address VARCHAR(500),
  capacity INT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

-- bookings
CREATE TABLE bookings (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  event_id UUID NOT NULL REFERENCES events(id),
  quantity INT NOT NULL,
  status VARCHAR(20),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

-- tickets
CREATE TABLE tickets (
  id UUID PRIMARY KEY,
  booking_id UUID NOT NULL REFERENCES bookings(id),
  event_id UUID NOT NULL REFERENCES events(id),
  code VARCHAR(100) UNIQUE NOT NULL,
  qr_code TEXT,
  status VARCHAR(20),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
```

### V2__passwordless_auth_cleanup.sql
- Dropped password-related columns (no longer used)
- Kept all identity mapping columns intact

---

## V. Core Infrastructure

### 5.1 Exception Handling (`core/exception/GlobalExceptionHandler.java`)
```java
@RestControllerAdvice
├─ @ExceptionHandler(MethodArgumentNotValidException)
│  ├─ Extracts first validation error
│  └─ Returns 400 { error: "field validation message" }
```

### 5.2 Constants (`core/constant/`)
- `UserRole.java`: USER, ADMIN
- `UserStatus.java`: ACTIVE, INACTIVE, SUSPENDED
- `IdentityUserType.java`: CCCD, PASSPORT
- `EventStatus.java`: DRAFT, PUBLISHED, ONGOING, FINISHED, CANCELLED
- `BookingStatus.java`: PENDING, CONFIRMED, CANCELLED, EXPIRED
- `TicketStatus.java`: VALID, USED, REVOKED, EXPIRED
- `CacheConstants.java`: Redis key prefixes

### 5.3 Validators (`core/validators/`)
- Custom validation annotations (if defined)

---

## VI. Configuration (`application.yml`)

```yaml
app:
  security:
    cors:
      allowed-origins: ${APP_SECURITY_CORS_ALLOWED_ORIGINS:http://localhost:3000}
    jwt:
      secret-key: ${JWT_SECRET_KEY}  # REQUIRED: 256+ bit secret
      access-token-expiration-ms: 900000  # 15 minutes
      refresh-token-expiration-ms: 604800000  # 7 days
      register-expiration-ms: 900000  # 15 minutes
      cookie-path: /api/v1/auth
      cookie-domain: localhost
    otp:
      validity-minutes: 5  # OTP expires after 5 min
      max-failed-attempts: 5  # Lock after 5 wrong attempts
    recaptcha:
      enabled: ${APP_SECURITY_RECAPTCHA_ENABLED:true}
      secret-key: ${RECAPCHA_SECRETS_KEY}  # REQUIRED if enabled
      verify-url: https://www.google.com/recaptcha/api/siteverify

spring:
  application:
    name: ticket-hunting
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: ${SPRING_DATA_REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  jpa:
    hibernate:
      ddl-auto: update  # Use 'update' temporarily; Flyway manages schema in production
    show-sql: true  # Enable to debug SQL queries
```

#### Required Environment Variables
```bash
# JWT
JWT_SECRET_KEY=your-256-bit-base64-secret

# PostgreSQL
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/eventmanager
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your-db-password

# Redis
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password

# Mail (Gmail SMTP)
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password  # Use Gmail App Password

# reCAPTCHA
APP_SECURITY_RECAPTCHA_ENABLED=true
RECAPCHA_SECRETS_KEY=your-recaptcha-secret-key

# CORS
APP_SECURITY_CORS_ALLOWED_ORIGINS=http://localhost:3000
```

---

## VII. Testing

### Test Configuration (`src/test/resources/application-test.yml`)
- Separate test configuration with mock/test credentials
- In-memory database or test database override

### Test Structure (`src/test/java/com/kietta/eventmanager/`)
- **`AuthControllerTest.java`**: Unit tests for auth endpoints
  - `sendOtp_success_returnsOk()`
  - `sendOtp_whenServiceThrows_returnsBadRequest()`
  - `verifyOtp_whenExistingUser_returnsOk()` → Status 200
  - `verifyOtp_whenNewUser_returnsAccepted()` → Status 202
  - `verifyOtp_whenLocked_returns429()` → Status 429
  - `completeRegister_success_returnsOk()`
  - `refreshToken_success_returnsOk()`
  - `logout_success_returnsOk()`

### Run Tests
```bash
./mvnw --no-transfer-progress test
```

---

## VIII. Developer Workflows

### Local Development
```bash
# 1. Start services (Docker)
docker compose up -d

# 2. Run tests
./mvnw --no-transfer-progress test

# 3. Start app
./mvnw spring-boot:run

# 4. Access at
curl http://localhost:8080/api/v1/auth/send-otp \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","recaptchaToken":"token"}'
```

### Production Deployment
```bash
# Build JAR (skip tests; run tests separately)
./mvnw clean package -DskipTests

# Or use Docker
docker compose up --build

# Verify with health check
curl http://localhost:8080/actuator/health
```

---

## IX. Project-Specific Patterns

### Response Format
All API responses follow a consistent pattern:

**Success**:
```json
{
  "accessToken": "eyJhbGc...",
  "tokenType": "Bearer",
  "refreshToken": "[HttpOnly Cookie]",
  "status": "LOGIN_SUCCESS | REGISTRATION_REQUIRED"
}
```

**Error**:
```json
{
  "error": "Error message in Vietnamese or English",
  "timestamp": "2026-03-31T12:00:00Z",
  "status": 400 | 429 | 500
}
```

### Email Normalization
All email addresses are normalized before processing:
```java
email.trim().toLowerCase(Locale.ROOT)
```

### OTP Semantics
- 5-minute validity
- 5 failed attempts before 429 lock
- Auto-clear after successful verification

### Token Rotation (Anti-Theft)
- Each refresh issues new access + refresh token
- Refresh token family tracked via `familyId` + `jti` in Redis
- If JTI doesn't match family, entire family revoked (token theft detected)

### Transactional Operations
- `@Transactional` on `completeRegister()` to ensure User + UserIdentity atomic creation
- No explicit transaction management; Spring handles via AOP

---

## X. Safe Change Rules for Agents

### Do's
✅ Prefer vertical slices (DTO + controller + service + repo + migration + test together)  
✅ Add new entities under `domain/{featureName}/entity`, services under `domain/{featureName}/service`  
✅ Keep email normalization consistent (`trim + lowercase`)  
✅ Always add Flyway migrations for schema changes (never alter applied migrations)  
✅ Update `AuthControllerTest` when auth contract changes  
✅ Verify status codes: `200` (existing user), `202` (new user), `429` (locked)  
✅ Use `@Transactional` for multi-entity operations  
✅ Add validation via `@Valid` + Bean Validation annotations  
✅ Return `ResponseEntity<?>` with error map payloads  

### Don'ts
❌ Don't hardcode secrets into YAML/properties  
❌ Don't modify already-applied Flyway migrations  
❌ Don't change CORS origins without updating frontend config  
❌ Don't use session-based auth (stateless only)  
❌ Don't expose refreshToken in response body (cookie only)  
❌ Don't remove `@CreationTimestamp` or `@UpdateTimestamp` from entities  
❌ Don't bypass email normalization  
❌ Don't create new DTOs without validation  

---

## XI. Quick Reference – File Locations

| Purpose | Path |
|---|---|
| Main entry point | `src/main/java/.../EventmanagerApplication.java` |
| Auth endpoints | `src/main/java/.../domain/auth/controller/AuthController.java` |
| Auth business logic | `src/main/java/.../domain/auth/service/AuthService.java` |
| User entities | `src/main/java/.../domain/user/entity/User.java` |
| User identities | `src/main/java/.../domain/user_identities/entity/UserIdentity.java` |
| Security config | `src/main/java/.../core/security/SecurityConfig.java` |
| JWT filter | `src/main/java/.../core/security/middleware/JwtAuthenticationFilter.java` |
| Constants | `src/main/java/.../core/constant/*.java` |
| Exception handler | `src/main/java/.../core/exception/GlobalExceptionHandler.java` |
| App config | `src/main/resources/application.yml` |
| Database migrations | `src/main/resources/db/migration/V{n}__*.sql` |
| Auth tests | `src/test/java/.../domain/auth/controller/AuthControllerTest.java` |
| Test config | `src/test/resources/application-test.yml` |

