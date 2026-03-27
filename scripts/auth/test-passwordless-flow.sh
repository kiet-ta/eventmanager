#!/usr/bin/env bash
set -euo pipefail

APP_URL="${APP_URL:-http://localhost:8080}"
EMAIL="${EMAIL:-new.user@example.com}"
RECAPTCHA_TOKEN="${RECAPTCHA_TOKEN:-}"
REDIS_CONTAINER="${REDIS_CONTAINER:-redis}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"

if [[ -z "$RECAPTCHA_TOKEN" ]]; then
  echo "ERROR: RECAPTCHA_TOKEN is required"
  exit 1
fi

if [[ -z "$REDIS_PASSWORD" ]]; then
  echo "ERROR: REDIS_PASSWORD is required"
  exit 1
fi

EMAIL_NORMALIZED="$(echo "$EMAIL" | tr '[:upper:]' '[:lower:]' | xargs)"

echo "[1/4] Send OTP to $EMAIL_NORMALIZED"
curl -sS -X POST "$APP_URL/api/v1/auth/send-otp" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL_NORMALIZED\",\"recaptchaToken\":\"$RECAPTCHA_TOKEN\"}" | tee /tmp/send_otp_response.json

echo
echo "[2/4] Read OTP from Redis key otp:$EMAIL_NORMALIZED"
OTP="$(docker exec "$REDIS_CONTAINER" redis-cli -a "$REDIS_PASSWORD" GET "otp:$EMAIL_NORMALIZED" | tr -d '\r')"
if [[ -z "$OTP" || "$OTP" == "(nil)" ]]; then
  echo "ERROR: OTP not found in Redis"
  exit 1
fi

echo "OTP=$OTP"

echo "[3/4] Verify OTP"
VERIFY_BODY="$(curl -sS -X POST "$APP_URL/api/v1/auth/verify-otp" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL_NORMALIZED\",\"otp\":\"$OTP\"}")"
echo "$VERIFY_BODY" | tee /tmp/verify_otp_response.json

STATUS="$(python - <<'PY'
import json
from pathlib import Path
p = Path('/tmp/verify_otp_response.json')
try:
    data = json.loads(p.read_text())
except Exception:
    print('UNKNOWN')
    raise SystemExit(0)
print(data.get('status', 'UNKNOWN'))
PY
)"

if [[ "$STATUS" == "REGISTRATION_REQUIRED" ]]; then
  REGISTER_TOKEN="$(python - <<'PY'
import json
from pathlib import Path
p = Path('/tmp/verify_otp_response.json')
data = json.loads(p.read_text())
print(data.get('registerToken', ''))
PY
)"

  if [[ -z "$REGISTER_TOKEN" ]]; then
    echo "ERROR: Missing registerToken in response"
    exit 1
  fi

  echo "[4/4] Complete register"
  curl -sS -X POST "$APP_URL/api/v1/auth/complete-register" \
    -H 'Content-Type: application/json' \
    -d "{\
      \"registerToken\":\"$REGISTER_TOKEN\",\
      \"firstName\":\"Auto\",\
      \"lastName\":\"Tester\",\
      \"identityNumber\":\"079123456789\",\
      \"identityType\":\"CCCD\"\
    }" | tee /tmp/complete_register_response.json
else
  echo "[4/4] Skip complete-register because status=$STATUS"
fi

echo
echo "Done. Responses saved in /tmp/*_response.json"

