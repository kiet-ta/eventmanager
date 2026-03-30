# Báo cáo triển khai Refresh Token (2026-03-30)

## 1) Mục tiêu

Triển khai đầy đủ luồng `accessToken + refreshToken` cho auth passwordless OTP:

- Sau `verify-otp` thành công (user đã tồn tại): trả về **cả accessToken và refreshToken**.
- Sau `complete-register`: trả về **cả accessToken và refreshToken**.
- Bổ sung endpoint:
  - `POST /api/v1/auth/refresh` để rotate refresh token và cấp cặp token mới.
  - `POST /api/v1/auth/logout` để revoke refresh token hiện tại.
- Lưu refresh token theo `jti` trên Redis để hỗ trợ revoke/rotation.

---

## 2) Kiến trúc sau triển khai

### 2.1 Token model

- `accessToken`
  - claim `purpose = ACCESS`
  - TTL: `app.security.jwt.expiration-ms`
- `refreshToken`
  - claim `purpose = REFRESH`
  - có `jti` duy nhất
  - TTL: `app.security.jwt.refresh-expiration-ms`
- `registerToken` (giữ nguyên)
  - claim `purpose = REGISTER`

### 2.2 Redis model cho refresh token

- Key: `refresh_token:{jti}`
- Value: `sha256(refreshToken)`
- TTL: bằng refresh token expiration

Cách này cho phép:
- xác thực refresh token đúng token đã phát hành,
- revoke chính xác theo `jti`,
- rotation an toàn (xóa token cũ, lưu token mới).

---

## 3) Luồng nghiệp vụ chi tiết

### 3.1 Send OTP

Không đổi:
1. FE gọi `/send-otp`.
2. BE verify reCAPTCHA, sinh OTP, lưu Redis OTP key.
3. Gửi OTP email.

### 3.2 Verify OTP (user cũ)

1. FE gọi `/verify-otp` với email + otp.
2. BE verify OTP và lock policy như cũ.
3. Nếu identity tồn tại:
   - phát hành access token,
   - phát hành refresh token (kèm `jti`),
   - lưu hash refresh token vào Redis theo `jti`.
4. Trả `200 OK`:

```json
{
  "status": "LOGIN_SUCCESS",
  "message": "OTP hop le, dang nhap thanh cong",
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "registerToken": null
}
```

### 3.3 Verify OTP (user mới)

Giữ nguyên:
- Trả `202 Accepted` + `registerToken`.

### 3.4 Complete register

1. FE gọi `/complete-register` với `registerToken` + profile info.
2. BE tạo user + identity.
3. BE phát hành cặp `accessToken + refreshToken` như luồng login.
4. Trả `200 OK`:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer"
}
```

### 3.5 Refresh token (rotation)

1. FE gửi `POST /api/v1/auth/refresh`:

```json
{ "refreshToken": "..." }
```

2. BE parse JWT refresh token:
   - kiểm tra chữ ký,
   - kiểm tra `purpose=REFRESH`,
   - đọc `userId/email/jti`.
3. Đối chiếu Redis key `refresh_token:{jti}` với hash token gửi lên.
4. Nếu hợp lệ:
   - revoke token cũ (`delete key cũ`),
   - phát hành access token mới,
   - phát hành refresh token mới (jti mới),
   - lưu Redis token mới.
5. Trả `200 OK` với cặp token mới.

Nếu không hợp lệ/hết hạn: trả `401`.

### 3.6 Logout

1. FE gọi `POST /api/v1/auth/logout` với refresh token.
2. BE parse token, lấy `jti`, delete key Redis.
3. Nếu token malformed/hết hạn vẫn trả `200` (idempotent logout).

---

## 4) Các thay đổi mã nguồn

### Backend

1. **Redis constants**
   - thêm `REFRESH_TOKEN_PREFIX`
   - File: `src/main/java/com/kietta/eventmanager/core/constant/CacheConstants.java`

2. **JWT service**
   - đổi `generateToken(...)` -> `generateAccessToken(...)`
   - thêm `generateRefreshToken(...)`
   - thêm parse payload refresh token `extractRefreshTokenPayload(...)`
   - thêm `getRefreshTokenExpiration()`
   - File: `src/main/java/com/kietta/eventmanager/domain/auth/service/JwtService.java`

3. **Refresh token storage service**
   - mới: `RefreshTokenService` (save/isValid/revoke, hash SHA-256)
   - File mới: `src/main/java/com/kietta/eventmanager/domain/auth/service/RefreshTokenService.java`

4. **Auth service**
   - `verifyOtp(...)`: login thành công trả access + refresh
   - `completeRegister(...)`: trả access + refresh
   - thêm `refreshAccessToken(...)`
   - thêm `logout(...)`
   - thêm helper `issueAuthTokens(...)`
   - File: `src/main/java/com/kietta/eventmanager/domain/auth/service/AuthService.java`

5. **DTOs**
   - `VerifyOtpResponse`: thêm `refreshToken`, cập nhật factory `loginSuccess(...)`
   - `AuthResponse`: thêm `refreshToken`, giữ constructor cũ để tương thích
   - thêm DTO mới `RefreshTokenRequest`
   - Files:
     - `src/main/java/com/kietta/eventmanager/domain/auth/dto/VerifyOtpResponse.java`
     - `src/main/java/com/kietta/eventmanager/domain/auth/dto/AuthResponse.java`
     - `src/main/java/com/kietta/eventmanager/domain/auth/dto/RefreshTokenRequest.java`

6. **Controller**
   - thêm endpoint:
     - `POST /api/v1/auth/refresh`
     - `POST /api/v1/auth/logout`
   - File: `src/main/java/com/kietta/eventmanager/domain/auth/controller/AuthController.java`

7. **Security config**
   - mở public cho 2 endpoint mới refresh/logout
   - File: `src/main/java/com/kietta/eventmanager/core/security/SecurityConfig.java`

8. **Config**
   - thêm `app.security.jwt.refresh-expiration-ms`
   - Files:
     - `src/main/resources/application.yml`
     - `src/test/resources/application-test.yml`

9. **Tests**
   - cập nhật `AuthControllerTest` theo DTO mới
   - thêm test cho `refreshToken_success_returnsOk`
   - thêm test cho `logout_success_returnsOk`
   - File: `src/test/java/com/kietta/eventmanager/domain/auth/controller/AuthControllerTest.java`

### Frontend

1. **Schema verify OTP**
   - thêm `refreshToken` vào nhánh `LOGIN_SUCCESS`
   - File: `src/features/auth/api/schemas.ts`

2. **Verify OTP form**
   - khi `LOGIN_SUCCESS`: lưu access/refresh/tokenType vào localStorage
   - điều hướng về `/`
   - khi `REGISTRATION_REQUIRED`: lưu `registerToken`
   - File: `src/features/auth/components/VerifyOtpForm.tsx`

---

## 5) Kiểm thử đã chạy

1. Backend tests:
- Lệnh: `./mvnw --no-transfer-progress test`
- Kết quả: **BUILD SUCCESS**, test pass.

2. Frontend build:
- Lệnh: `npm run build`
- Kết quả: **build thành công**.

---

## 6) API contract mới

### 6.1 Verify OTP (user cũ)
- `POST /api/v1/auth/verify-otp`
- Response `200` có thêm `refreshToken`.

### 6.2 Complete register
- `POST /api/v1/auth/complete-register`
- Response `200` có thêm `refreshToken`.

### 6.3 Refresh
- `POST /api/v1/auth/refresh`
- Request:
```json
{ "refreshToken": "..." }
```
- Response `200`:
```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer"
}
```

### 6.4 Logout
- `POST /api/v1/auth/logout`
- Request:
```json
{ "refreshToken": "..." }
```
- Response `200`:
```json
{ "message": "Dang xuat thanh cong" }
```

---

## 7) Ghi chú vận hành

- Hiện tại FE lưu refresh token trong localStorage để nhanh chóng tích hợp. Đây **không phải** phương án tối ưu bảo mật cho production.
- Khuyến nghị bước tiếp theo:
  - chuyển refresh token sang `HttpOnly Secure Cookie`,
  - FE không truy cập trực tiếp refresh token,
  - thêm interceptor tự động gọi `/refresh` khi access token hết hạn.

---

## 8) Checklist bàn giao

- [x] Có refresh token khi verify OTP thành công.
- [x] Có refresh token khi complete register.
- [x] Có endpoint refresh với rotation.
- [x] Có endpoint logout revoke refresh token.
- [x] Cập nhật test backend pass.
- [x] Build frontend pass.
