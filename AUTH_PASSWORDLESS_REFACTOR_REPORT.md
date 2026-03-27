# Passwordless Auth Refactor Report

## Scope
Refactor luong dang nhap/dang ky sang OTP passwordless theo 3 buoc:
1) `send-otp` chung cho login/register
2) `verify-otp` re nhanh login hoac new-user
3) `complete-register` cho user moi bang `registerToken`

## Files Changed
- `src/main/java/com/kietta/eventmanager/domain/auth/controller/AuthController.java`
  - Bo endpoint cu `/login`, `/register`
  - Them endpoints moi:
    - `POST /api/v1/auth/send-otp`
    - `POST /api/v1/auth/verify-otp`
    - `POST /api/v1/auth/complete-register`
  - Tra ve HTTP `202` khi OTP dung nhung la user moi, `429` khi lock OTP

- `src/main/java/com/kietta/eventmanager/domain/auth/service/AuthService.java`
  - Bo login/password va register co `password`
  - Them logic:
    - `sendOtp(...)`: verify reCAPTCHA, tao OTP, gui email
    - `verifyOtp(...)`: check lock -> verify OTP -> login success hoac issue registerToken
    - `completeRegister(...)`: validate registerToken, tao `users` + `user_identities`, issue access token

- `src/main/java/com/kietta/eventmanager/domain/auth/service/OtpService.java`
  - Them lock/fail-counter trong Redis
  - Redis keys:
    - `otp:{email}` (TTL 5 phut)
    - `otp_fails:{email}` (TTL 5 phut)
  - Rule:
    - Sai OTP thi tang counter
    - `>=5` lan sai tra `429`
    - OTP dung thi xoa ca 2 key

- `src/main/java/com/kietta/eventmanager/domain/auth/service/JwtService.java`
  - Them register token:
    - `generateRegisterToken(email)`
    - `extractRegisterEmail(token)`
  - Access token flow giu nguyen

- `src/main/java/com/kietta/eventmanager/domain/auth/service/NotificationService.java`
  - Doi method thanh `sendOtpCode(...)`

- `src/main/java/com/kietta/eventmanager/domain/auth/service/EmailNotificationServiceImpl.java`
  - Cap nhat noi dung email OTP
  - Dung method moi `sendOtpCode(...)`

- `src/main/java/com/kietta/eventmanager/domain/user_identities/entity/UserIdentity.java`
  - Xoa field `passwordHash`
  - Chinh `@JoinColumn(name = "user_id")` cho dung schema

- `src/main/java/com/kietta/eventmanager/domain/user_identities/repository/UserIdentityRepository.java`
  - Them method ignore-case cho provider/providerId de lookup email on dinh

- `src/main/java/com/kietta/eventmanager/core/constant/CacheConstants.java`
  - Them `OTP_FAILS_PREFIX`

- `src/main/resources/application.yml`
  - Bo config duplicate o `spring.application.security`
  - Them:
    - `app.security.otp.max-failed-attempts`
    - `app.security.jwt.register-expiration-ms`

- `src/main/resources/db/migration/V2__passwordless_auth_cleanup.sql`
  - Drop cot `password_hash` khoi bang `user_identities`

- DTO moi trong `src/main/java/com/kietta/eventmanager/domain/auth/dto/`
  - `SendOtpRequest`
  - `VerifyOtpRequest`
  - `VerifyOtpResponse`
  - `CompleteRegisterRequest`

- `src/test/java/com/kietta/eventmanager/domain/auth/controller/AuthControllerTest.java`
  - Refactor test theo API moi, cover status `200/202/429`

- `AGENTS.md`
  - Viet lai guide theo dung hien trang project

## Services Impacted
- **AuthService**: thay doi lon nhat (orchestration flow)
- **OtpService**: them lock + attempt counter
- **JwtService**: them register token path
- **RecaptchaService**: tiep tuc verify token trong `send-otp`
- **NotificationService / EmailNotificationServiceImpl**: doi contract gui OTP

## API Behavior After Refactor
- `POST /api/v1/auth/send-otp`
  - Input: `email`, `recaptchaToken`
  - Output: `200` neu gui OTP thanh cong
- `POST /api/v1/auth/verify-otp`
  - Input: `email`, `otp`
  - Output:
    - `200` + access token neu user da ton tai
    - `202` + registerToken neu user moi
    - `429` neu sai OTP >= 5 lan trong khung TTL
- `POST /api/v1/auth/complete-register`
  - Input: `registerToken`, `firstName`, `lastName`, `identityNumber`, `identityType`
  - Output: `200` + access token

## Phase 2 Follow-up (Da trien khai)

### Step 1 - Remove DTO cu
- Da xoa:
  - `src/main/java/com/kietta/eventmanager/domain/auth/dto/LoginRequest.java`
  - `src/main/java/com/kietta/eventmanager/domain/auth/dto/RegisterRequest.java`
- Ly do:
  - 2 DTO nay thuoc luong login/register bang password cu, khong con endpoint nao su dung.
  - Tranh confusion khi doc codebase va generate code tiep theo.

### Step 2 - Cho phep `EventmanagerApplicationTests` chay khong can DB that
- Da them dependency test:
  - `pom.xml` -> `com.h2database:h2` (scope `test`)
- Da them profile test:
  - `src/test/resources/application-test.yml`
  - Cau hinh chinh:
    - datasource H2 in-memory (`jdbc:h2:mem:eventmanager-test;MODE=PostgreSQL`)
    - `spring.flyway.enabled=false`
    - `spring.jpa.hibernate.ddl-auto=create-drop`
    - set dummy values cho `app.security.*`, redis, mail de context boot duoc
- Da kich hoat test profile:
  - `src/test/java/com/kietta/eventmanager/EventmanagerApplicationTests.java`
  - them `@ActiveProfiles("test")`

### Step 3 - Bo tai lieu + script test bang curl khi chay Docker
- Da them guide:
  - `docs/curl-passwordless-auth.md`
  - Noi dung: command test `send-otp`, doc OTP tu Redis, `verify-otp`, `complete-register`, test lock `429`
- Da them script tu dong:
  - `scripts/auth/test-passwordless-flow.sh`
  - Script tu chay full flow:
    1. call `send-otp`
    2. read OTP key `otp:{email}` trong container Redis
    3. call `verify-otp`
    4. neu `REGISTRATION_REQUIRED` thi goi `complete-register`

## Verification

### Unit test auth controller (da pass)
```bash
cd /home/tak/Code/eventmanager
./mvnw --no-transfer-progress -Dtest=AuthControllerTest test
```

### Full suite
- Da verify lai full suite va pass (`Tests run: 8, Failures: 0, Errors: 0`).
- Lenh da chay:
```bash
cd /home/tak/Code/eventmanager
./mvnw --no-transfer-progress test
```

### Docker curl flow (thu cong)
```bash
cd /home/tak/Code/eventmanager
docker compose up --build
```

```bash
export APP_URL="http://localhost:8080"
export EMAIL="new.user@example.com"
export RECAPTCHA_TOKEN="<real-token>"
export REDIS_CONTAINER="redis"
export REDIS_PASSWORD="<redis-password>"

./scripts/auth/test-passwordless-flow.sh
```

## Remaining Recommendations
- Them integration test cho `OtpService` de assert TTL + fail counter + lock threshold.
- Neu local khong muon phu thuoc reCAPTCHA that, tao test profile/service mock cho `RecaptchaService` trong moi truong dev.



