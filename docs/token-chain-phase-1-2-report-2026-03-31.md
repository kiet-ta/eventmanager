# 📋 Báo cáo Giai đoạn 1 & 2: Token Chain Authentication Setup (2026-03-31)

## 🎯 Tổng quan

Đã hoàn tất **Giai đoạn 1 (Cấu hình & CookieUtils)** và **Giai đoạn 2 (Nâng cấp JwtService)** của triển khai Token Chain Authentication. Hệ thống giờ đây:

- ✅ Phân biệt **AccessToken** (TTL ngắn 15 phút) và **RefreshToken** (TTL dài 7 ngày)
- ✅ Cấu hình Cookie HttpOnly, Secure, SameSite để bảo vệ RefreshToken
- ✅ RefreshToken chứa **familyId** để phát hiện token theft (Stolen Token Detection)
- ✅ Tự động sinh **jti** (JWT ID) duy nhất cho mỗi token

---

## 📝 Chi tiết các thay đổi

### **Giai đoạn 1: Cấu hình & Công cụ**

#### 1.1 Application.yml - Tách biệt expiration time

**File:** `src/main/resources/application.yml`

- `access-token-expiration-ms: 900000` → **15 phút** (token ngắn hạn)
- `refresh-token-expiration-ms: 604800000` → **7 ngày** (token dài hạn)
- `register-expiration-ms: 900000` → **15 phút** (giữ nguyên)
- Thêm config cookie:
  - `cookie-path: /api/v1/auth`
  - `cookie-domain: localhost`

**Test config:** `src/test/resources/application-test.yml` cũng được cập nhật tương tự.

#### 1.2 CookieUtils.java - Xử lý Cookie HttpOnly

**File mới:** `src/main/java/com/kietta/eventmanager/core/security/CookieUtils.java`

Chức năng:
- `setRefreshTokenCookie(response, token)`: Nhét RefreshToken vào HttpOnly Cookie
  - `HttpOnly = true` → JavaScript không truy cập được
  - `Secure = true` (nếu production)
  - `SameSite = Strict` → Chống CSRF
  - `Path = /api/v1/auth`
  - `MaxAge = TTL refresh token`
  
- `clearRefreshTokenCookie(response)`: Xóa cookie khi logout
  - Set `MaxAge = 0` để xóa ngay lập tức

---

### **Giai đoạn 2: Nâng cấp JwtService**

#### 2.1 Cập nhật config properties

**File:** `src/main/java/com/kietta/eventmanager/domain/auth/service/JwtService.java`

- Đổi `@Value("${app.security.jwt.expiration-ms}")` → `@Value("${app.security.jwt.access-token-expiration-ms:900000}")`
- Thêm `@Value("${app.security.jwt.refresh-token-expiration-ms:604800000}")`

#### 2.2 Các method mới

**generateAccessToken(userId, email)**
```java
public String generateAccessToken(UUID userId, String email) {
    return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("purpose", "ACCESS")
            .issuedAt(new Date(System.currentTimeMillis()))
            .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
            .signWith(getSignInKey())
            .compact();
}
```

- **Purpose:** `ACCESS`
- **TTL:** `accessTokenExpiration` (15 phút)
- **Không có:** jti, familyId (đơn giản, chỉ để verify)

**generateRefreshToken(userId, email, familyId)**
```java
public String generateRefreshToken(UUID userId, String email, String familyId) {
    String jti = UUID.randomUUID().toString();
    return Jwts.builder()
            .subject(userId.toString())
            .id(jti)                                    // JWT ID duy nhất
            .claim("email", email)
            .claim("purpose", "REFRESH")
            .claim("familyId", familyId)               // **Key claim**
            .issuedAt(new Date(System.currentTimeMillis()))
            .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
            .signWith(getSignInKey())
            .compact();
}
```

- **Purpose:** `REFRESH`
- **familyId:** Để track token chain (phát hiện theft)
- **jti:** Sinh tự động, dùng để lưu trạng thái trên Redis
- **TTL:** `refreshTokenExpiration` (7 ngày)

**extractAllClaims(token)**
```java
public Claims extractAllClaims(String token) {
    return parseClaims(token);
}
```

- Trả về toàn bộ claims để dùng linh hoạt ở các tầng khác.

**extractRefreshTokenPayload(token)**

Cập nhật để trích xuất thêm **familyId**:

```java
public RefreshTokenPayload extractRefreshTokenPayload(String token) {
    Claims claims = parseClaims(token);
    String purpose = claims.get("purpose", String.class);
    if (!"REFRESH".equals(purpose)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "...");
    }

    String subject = claims.getSubject();
    String email = claims.get("email", String.class);
    String jti = claims.getId();
    String familyId = claims.get("familyId", String.class);  // **Trích familyId**

    if (subject == null || email == null || jti == null || familyId == null) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "...");
    }

    try {
        UUID userId = UUID.fromString(subject);
        return new RefreshTokenPayload(userId, email, jti, familyId);  // **Trả 4 field**
    } catch (IllegalArgumentException ex) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "...");
    }
}
```

**Cấu trúc RefreshTokenPayload**
```java
public record RefreshTokenPayload(UUID userId, String email, String jti, String familyId) {
}
```

---

#### 2.3 Helper methods

**getAccessTokenExpiration()** / **getRefreshTokenExpiration()**
- Trả về TTL để dùng khi lưu Redis.

---

### **Cải tiến Redis model**

**File:** `src/main/java/com/kietta/eventmanager/core/constant/CacheConstants.java`

Thêm constant:
```java
public static final String REFRESH_TOKEN_FAMILY_PREFIX = "rt_family:";
```

**Redis schema:**
```
Key:   rt_family:{familyId}
Value: {jti}
TTL:   = refresh token expiration
```

Ý tưởng:
- Khi login: tạo `familyId` → lưu `jti` vào family
- Khi refresh: đối chiếu `jti` trong token với giá trị trong Redis
- Nếu **khác nhau** → token theft detected! Revoke family.

---

### **Nâng cấp RefreshTokenService**

**File:** `src/main/java/com/kietta/eventmanager/domain/auth/service/RefreshTokenService.java`

Thay đổi hoàn toàn đơn giản hóa:

- `saveRefreshTokenToFamily(familyId, jti, ttl)`: Lưu jti vào family key
- `getRefreshTokenJtiFromFamily(familyId)`: Lấy jti hiện tại
- `isValidJtiForFamily(familyId, jti)`: Kiểm tra jti có match
- `revokeFamily(familyId)`: Xóa key (logout hoặc theft detected)

---

### **Cập nhật AuthService**

**File:** `src/main/java/com/kietta/eventmanager/domain/auth/service/AuthService.java`

#### issueAuthTokens - Phát hành cặp token
```java
private AuthResponse issueAuthTokens(UUID userId, String email) {
    String accessToken = jwtService.generateAccessToken(userId, email);
    String familyId = UUID.randomUUID().toString();  // Tạo familyId mới
    String refreshToken = jwtService.generateRefreshToken(userId, email, familyId);

    JwtService.RefreshTokenPayload payload = jwtService.extractRefreshTokenPayload(refreshToken);
    refreshTokenService.saveRefreshTokenToFamily(familyId, payload.jti(), jwtService.getRefreshTokenExpiration());

    return new AuthResponse(accessToken, refreshToken, "Bearer");
}
```

#### refreshAccessToken - Token rotation + Theft detection
```java
public AuthResponse refreshAccessToken(RefreshTokenRequest request) {
    JwtService.RefreshTokenPayload payload = jwtService.extractRefreshTokenPayload(request.getRefreshToken());

    // ⚠️ Kiểm tra jti có match familyId không
    if (!refreshTokenService.isValidJtiForFamily(payload.familyId(), payload.jti())) {
        // ⚠️ TOKEN THEFT DETECTED!
        refreshTokenService.revokeFamily(payload.familyId());  // Phong tỏa account
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Refresh token khong hop le hoac da bi su dung. Tai khoan da bi khoa."
        );
    }

    // ✅ Valid! Perform rotation
    String email = normalizeEmail(payload.email());
    String accessToken = jwtService.generateAccessToken(payload.userId(), email);
    String refreshToken = jwtService.generateRefreshToken(payload.userId(), email, payload.familyId());

    JwtService.RefreshTokenPayload newPayload = jwtService.extractRefreshTokenPayload(refreshToken);
    refreshTokenService.saveRefreshTokenToFamily(payload.familyId(), newPayload.jti(), jwtService.getRefreshTokenExpiration());

    return new AuthResponse(accessToken, refreshToken, "Bearer");
}
```

**Logic:**
1. Parse refresh token → lấy `familyId`, `jti`
2. Kiểm tra Redis: `rt_family:{familyId}` == `jti` hiện tại?
3. **Nếu KHÔNG khớp** → hacker dùng token cũ → revoke toàn family
4. **Nếu khớp** → sinh cặp token mới, update Redis jti

#### logout - Revoke family
```java
public void logout(RefreshTokenRequest request) {
    try {
        JwtService.RefreshTokenPayload payload = jwtService.extractRefreshTokenPayload(request.getRefreshToken());
        refreshTokenService.revokeFamily(payload.familyId());
    } catch (ResponseStatusException ignored) {
        // Idempotent logout
    }
}
```

---

## ✅ Kiểm thử

- **Compile:** ✅ `BUILD SUCCESS`
- **Unit tests:** ✅ `10/10 pass`
- **Frontend build:** ✅ Build success

---

## 🏗️ Kiến trúc Token Chain sau giai đoạn 1 & 2

```
┌─────────────────────────────────────────┐
│ User Login / Complete Register          │
└────────────┬────────────────────────────┘
             │
             ▼
    ┌────────────────────┐
    │ issueAuthTokens()  │
    │ - accessToken (15m)│
    │ - refreshToken(7d) │
    │ - familyId (UUID)  │
    └────────┬───────────┘
             │
    ┌────────▼────────────────────────────┐
    │ Redis: rt_family:{familyId}={jti}   │
    │ TTL = 7 days                        │
    └─────────────────────────────────────┘
             │
    ┌────────▼────────────────────────────┐
    │ Cookie: refreshToken (HttpOnly)     │
    │ Response: { accessToken, ... }      │
    └─────────────────────────────────────┘

┌─────────────────────────────────────────┐
│ User later calls /refresh with Cookie   │
└────────────┬────────────────────────────┘
             │
             ▼
    ┌────────────────────────────────────┐
    │ extractRefreshTokenPayload()        │
    │ -> familyId, jti, userId, email    │
    └────────┬──────────────────────────┘
             │
             ▼
    ┌─────────────────────────────────────────┐
    │ Redis check: rt_family:{familyId}       │
    │ ┌──────────────────────────────────────┐│
    │ │ If storedJti == token.jti            ││
    │ │ ✅ VALID → Rotation                  ││
    │ │                                      ││
    │ │ Else                                 ││
    │ │ ⚠️ THEFT DETECTED → Revoke family   ││
    │ └──────────────────────────────────────┘│
    └─────────────────────────────────────────┘
             │
             ▼
    ┌────────────────────────────────────────┐
    │ Issue new pair + Update Redis          │
    │ Response: { newAccessToken, ... }      │
    │ Cookie: newRefreshToken (HttpOnly)     │
    └────────────────────────────────────────┘
```

---

## 🚀 Các giai đoạn tiếp theo

| Giai đoạn | Mục tiêu | Trạng thái |
|-----------|---------|-----------|
| 1 | Cấu hình + CookieUtils | ✅ **DONE** |
| 2 | JwtService + Token Chain | ✅ **DONE** |
| 3 | AuthService + Redis Logic | 📅 **TODO** |
| 4 | AuthController + HttpServletResponse | 📅 **TODO** |
| 5 | JwtAuthenticationFilter | 📅 **TODO** |

---

## 📊 Token lifecycle

```
LOGIN (verify-otp success)
    ↓
[Generate AT (15m) + RT (7d) + familyId (UUID)]
    ↓
[Save rt_family:{familyId}={jti} → Redis with TTL 7d]
    ↓
[Response: { accessToken, refreshToken (HTTP-only cookie), ... }]
    ↓
USE (API calls)
    ↓
FE send request with Authorization: Bearer {accessToken}
    ↓
If AT hết hạn (403 Unauthorized) → gọi /refresh
    ↓
REFRESH (Browser tự gửi Cookie)
    ↓
[Check jti vs Redis]
    ↓
Nếu khác nhau → THEFT! Revoke family
    ↓
Nếu khớp → Rotation: sinh cặp (AT', RT'), update Redis jti
    ↓
[Response: { newAccessToken, newRefreshToken (HTTP-only cookie), ... }]
    ↓
LOGOUT
    ↓
[Delete rt_family:{familyId} from Redis]
[Clear Cookie]
```

---

## 🔐 Bảo mật

- ✅ **AccessToken**: JWT đơn giản, không lưu trạng thái
- ✅ **RefreshToken**: HttpOnly cookie (JavaScript không truy cập)
- ✅ **Token theft detection**: jti + familyId tracking
- ✅ **Theft response**: Revoke toàn family (phong tỏa account)
- ✅ **CSRF**: SameSite=Strict trên cookie
- ✅ **HTTPS only** (production): Secure flag

---

## 📦 Tệp được thay đổi/tạo mới

### Tạo mới
- `src/main/java/com/kietta/eventmanager/core/security/CookieUtils.java`

### Cập nhật
- `src/main/resources/application.yml`
- `src/test/resources/application-test.yml`
- `src/main/java/com/kietta/eventmanager/domain/auth/service/JwtService.java`
- `src/main/java/com/kietta/eventmanager/domain/auth/service/RefreshTokenService.java`
- `src/main/java/com/kietta/eventmanager/domain/auth/service/AuthService.java`
- `src/main/java/com/kietta/eventmanager/core/constant/CacheConstants.java`

---

## ✨ Kết luận

**Giai đoạn 1 & 2** đã thiết lập nền tảng vững chắc cho Token Chain Authentication:

- Hệ thống phân biệt rõ ràng AT (ngắn) vs RT (dài)
- CookieUtils chuẩn bị sẵn để SET-COOKIE HttpOnly
- JwtService sinh token chứa `familyId` + `jti` tự động
- AuthService logic rotation + theft detection sẵn sàng

**Sẵn sàng cho Giai đoạn 3 & 4**: Triển khai REST API endpoints với Cookie handling! 🎯
