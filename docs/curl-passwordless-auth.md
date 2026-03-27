# Curl Test Guide (Docker)

Tai lieu nay huong dan test 3 endpoint auth passwordless khi chay bang Docker.

## Prerequisites
- Da chay stack:
  ```bash
  docker compose up --build
  ```
- Da set `REDIS_PASSWORD` trong `.env` (vi Redis dang bat auth)
- Co reCAPTCHA token hop le (neu BE dang verify that)

## Environment Variables
```bash
export APP_URL="http://localhost:8080"
export EMAIL="new.user@example.com"
export RECAPTCHA_TOKEN="paste-real-token"
export REDIS_CONTAINER="redis"
export REDIS_PASSWORD="your-redis-password"
```

## 1) Send OTP
```bash
curl -i -X POST "$APP_URL/api/v1/auth/send-otp" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"recaptchaToken\":\"$RECAPTCHA_TOKEN\"}"
```

Expected: `200 OK`

## 2) Lay OTP tu Redis (chi de local test)
```bash
OTP=$(docker exec "$REDIS_CONTAINER" redis-cli -a "$REDIS_PASSWORD" GET "otp:${EMAIL,,}" | tr -d '\r')
echo "$OTP"
```

## 3) Verify OTP
```bash
curl -i -X POST "$APP_URL/api/v1/auth/verify-otp" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"$OTP\"}"
```

Expected:
- `200 OK` neu email da ton tai (`accessToken`)
- `202 Accepted` neu email moi (`registerToken`)

## 4) Neu la user moi, complete register
Lay `registerToken` tu response `verify-otp`, sau do goi:

```bash
REGISTER_TOKEN="paste-register-token"

curl -i -X POST "$APP_URL/api/v1/auth/complete-register" \
  -H 'Content-Type: application/json' \
  -d "{\
    \"registerToken\":\"$REGISTER_TOKEN\",\
    \"firstName\":\"New\",\
    \"lastName\":\"User\",\
    \"identityNumber\":\"079123456789\",\
    \"identityType\":\"CCCD\"\
  }"
```

Expected: `200 OK` + `accessToken`

## 5) Test lock (`429`)
Nhap sai OTP >= 5 lan trong TTL:
```bash
for _ in 1 2 3 4 5; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST "$APP_URL/api/v1/auth/verify-otp" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"otp\":\"000000\"}"
done
```

Expected: lan cuoi tra `429`

