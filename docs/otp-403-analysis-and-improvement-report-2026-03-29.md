# OTP Login 403 Analysis & Improvement Report (2026-03-29)

## 1) Executive summary

Trong luồng gửi OTP từ frontend sang backend, lỗi `403` xuất hiện chủ yếu do **CORS mismatch** (origin frontend không nằm trong danh sách allowed origin của backend).

Ngoài ra, còn có lỗi hợp đồng API thứ hai: backend yêu cầu `recaptchaToken` nhưng frontend chưa gửi field này, dẫn tới lỗi request sau khi đã vượt qua CORS.

Bản triển khai hiện tại đã xử lý cả 2 nhóm vấn đề ở mức vận hành local/dev và cải thiện nền tảng để harden production.

---

## 2) Root cause chi tiết

### RC-1: CORS cấu hình cứng 1 origin
- Backend trước đó chỉ allow `http://localhost:3000`.
- Khi frontend chạy bằng `http://127.0.0.1:3000` hoặc host khác (LAN/tunnel/domain dev), preflight/actual request có thể bị Spring Security từ chối với `403`.

### RC-2: Contract mismatch `send-otp`
- Backend endpoint `POST /api/v1/auth/send-otp` có `recaptchaToken` trong request DTO.
- Frontend gửi payload chỉ có `email`, không có `recaptchaToken`.
- Kết quả: request fail validation/business check (thường `400`) ngay cả khi CORS đã đúng.

### RC-3: Error payload mapping chưa đầy đủ ở frontend
- Backend trả lỗi dạng `{ "error": "..." }`.
- Frontend API client ưu tiên đọc `{ "message": "..." }`.
- Người dùng có thể nhận thông báo lỗi kém rõ nghĩa.

---

## 3) Các thay đổi đã triển khai

## 3.1 Backend hardening + dev friendliness

### A. CORS chuyển sang cấu hình động
- File cập nhật: [src/main/java/com/kietta/eventmanager/core/security/SecurityConfig.java](../src/main/java/com/kietta/eventmanager/core/security/SecurityConfig.java)
- Thay đổi:
  - Đọc origin từ cấu hình: `app.security.cors.allowed-origins`
  - Parse danh sách origin dạng CSV
  - Set `maxAge` cho preflight
- Mặc định local an toàn hơn:
  - `http://localhost:3000`
  - `http://127.0.0.1:3000`

### B. Thêm config CORS + reCAPTCHA enable flag
- File cập nhật: [src/main/resources/application.yml](../src/main/resources/application.yml)
- Bổ sung:
  - `app.security.cors.allowed-origins`
  - `app.security.recaptcha.enabled`

### C. Cho phép local dev tắt reCAPTCHA đúng cách
- File cập nhật: [src/main/java/com/kietta/eventmanager/domain/auth/service/RecaptchaService.java](../src/main/java/com/kietta/eventmanager/domain/auth/service/RecaptchaService.java)
- Thay đổi:
  - Nếu `app.security.recaptcha.enabled=false` thì bỏ qua verify token.
  - Nếu bật reCAPTCHA thì vẫn enforce token và gọi Google verify như cũ.

### D. Nới validation DTO theo hướng conditional
- File cập nhật: [src/main/java/com/kietta/eventmanager/domain/auth/dto/SendOtpRequest.java](../src/main/java/com/kietta/eventmanager/domain/auth/dto/SendOtpRequest.java)
- Thay đổi:
  - Bỏ `@NotBlank` khỏi `recaptchaToken` để không fail binding ở local khi reCAPTCHA bị tắt.
  - Validation thực tế chuyển về service theo cờ `recaptcha.enabled`.

---

## 3.2 Frontend contract alignment

### A. Schema gửi OTP hỗ trợ `recaptchaToken`
- File cập nhật: [../event-manager-fe/src/features/auth/api/schemas.ts](../../event-manager-fe/src/features/auth/api/schemas.ts)
- Thay đổi:
  - `sendOtpReqSchema` thêm trường optional `recaptchaToken`.

### B. API call gửi kèm token nếu có
- File cập nhật: [../event-manager-fe/src/features/auth/api/send-otp.ts](../../event-manager-fe/src/features/auth/api/send-otp.ts)
- Thay đổi:
  - Payload gửi thêm `recaptchaToken`.

### C. Form tích hợp reCAPTCHA v2 checkbox
- File cập nhật: [../event-manager-fe/src/features/auth/components/SendOtpForm.tsx](../../event-manager-fe/src/features/auth/components/SendOtpForm.tsx)
- Thay đổi:
  - Thêm widget `reCAPTCHA v2` checkbox.
  - Lấy token từ widget và gửi qua `recaptchaToken` khi submit.
  - Nếu bật `VITE_RECAPTCHA_ENABLED=true` mà thiếu token/site-key thì chặn submit với thông báo rõ ràng.
  - Nếu môi trường local tắt reCAPTCHA thì form vẫn chạy bình thường.

### D. Error mapping đọc cả `error`
- File cập nhật: [../event-manager-fe/src/lib/api-client.ts](../../event-manager-fe/src/lib/api-client.ts)
- Thay đổi:
  - `getErrorMessage()` hỗ trợ payload `{ error: string }` ngoài `{ message: string }`.

---

## 4) Hướng dẫn vận hành sau fix

## 4.1 Local dev (khuyến nghị)

1. Backend:
   - Set biến môi trường:
     - `APP_SECURITY_RECAPTCHA_ENABLED=false`
     - (tuỳ chọn) `APP_SECURITY_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000`

2. Frontend:
   - Đảm bảo `VITE_API_BASE_URL` trỏ đúng backend (ví dụ `http://localhost:8080`).

3. Chạy lại backend + frontend và thử gửi OTP.

## 4.2 Production/staging

- Bật lại reCAPTCHA:
  - `APP_SECURITY_RECAPTCHA_ENABLED=true`
- Frontend cần tích hợp widget/token thật từ Google (không nên dùng nhập tay token).
- Chỉ allow origin thực tế của frontend trong `APP_SECURITY_CORS_ALLOWED_ORIGINS`.

---

## 5) Kế hoạch cải tiến tiếp theo (đề xuất)

## Phase 1 (ngắn hạn: 1-2 ngày)
1. Chuẩn hóa response lỗi backend theo format thống nhất:
   - `{ code, message, details }`
2. Thêm log correlation-id cho auth flow.
3. Bổ sung tài liệu env `.env.example` cho FE/BE.

## Phase 2 (trung hạn: 2-4 ngày)
1. Tích hợp reCAPTCHA widget thực tế trên frontend.
2. Bỏ input token thủ công trong UI.
3. Thêm kiểm thử integration cho `send-otp` với 2 mode:
   - reCAPTCHA enabled
   - reCAPTCHA disabled (local profile)

## Phase 3 (hardening)
1. Tăng cường rate limit theo IP + email cho endpoint OTP.
2. Bổ sung audit trail cho các trạng thái OTP (send/verify/lock).
3. Cấu hình CORS theo environment tách biệt rõ dev/staging/prod.

---

## 6) Checklist xác nhận fix

- [x] CORS không còn hardcode 1 origin
- [x] Có thể cấu hình origin bằng env
- [x] Có cờ bật/tắt reCAPTCHA cho local dev
- [x] Frontend có thể gửi `recaptchaToken`
- [x] Frontend hiển thị tốt lỗi dạng `{ error: ... }`

---

## 7) Risk notes

- Nếu production đặt `APP_SECURITY_RECAPTCHA_ENABLED=false` sẽ giảm mức bảo vệ bot.
- Input token thủ công ở frontend chỉ là giải pháp chuyển tiếp; cần thay bằng widget thật ở phase kế tiếp.

---

## 8) Kết luận

Lỗi `403` đã được xử lý từ gốc bằng việc cấu hình CORS linh hoạt theo môi trường. Đồng thời contract `send-otp` FE/BE đã được đồng bộ và nâng khả năng debug lỗi cho người dùng. Hệ thống hiện có đường chạy local ổn định hơn, đồng thời vẫn giữ khả năng harden production qua cấu hình.

---

## 9) Phương án triển khai reCAPTCHA widget chuẩn (v2/v3)

## 9.1 Mục tiêu
- Chống bot spam endpoint `send-otp`
- Giữ trải nghiệm người dùng mượt trên frontend
- Cho phép local/dev chạy không phụ thuộc Google bằng config môi trường

## 9.2 Kiến trúc chung
1. Frontend lấy token từ widget reCAPTCHA.
2. Frontend gửi token qua `recaptchaToken` khi gọi `POST /api/v1/auth/send-otp`.
3. Backend verify token với Google tại `RecaptchaService`.
4. Chỉ khi verify thành công mới tiếp tục generate + gửi OTP.

## 9.3 Option A: reCAPTCHA v2 Checkbox (khuyến nghị nếu ưu tiên đơn giản)

### Frontend
- Dùng package React phổ biến (ví dụ `react-google-recaptcha`).
- Cấu hình env:
  - `VITE_RECAPTCHA_SITE_KEY`
- Render widget ở form gửi OTP.
- Khi submit:
  - Lấy token từ widget.
  - Nếu chưa có token thì chặn submit và báo lỗi.
  - Gửi token vào `sendOtp` API.

### Backend
- Giữ `app.security.recaptcha.enabled=true` cho staging/prod.
- Giữ `app.security.recaptcha.secret-key` đúng môi trường.
- Verify như hiện tại qua Google endpoint.

### Ưu / Nhược
- Ưu: dễ triển khai, hành vi rõ ràng cho user.
- Nhược: thêm 1 bước click cho người dùng.

## 9.4 Option B: reCAPTCHA v3 Score-based (khuyến nghị nếu ưu tiên UX)

### Frontend
- Dùng SDK/React wrapper tương ứng v3.
- Cấu hình env:
  - `VITE_RECAPTCHA_SITE_KEY`
- Trước khi submit, gọi execute action `send_otp` để lấy token.
- Gửi token vào API.

### Backend
- Verify token như hiện tại và đọc thêm score/action từ response Google.
- Bổ sung ngưỡng score, ví dụ:
  - `app.security.recaptcha.min-score=0.5`
  - `app.security.recaptcha.expected-action=send_otp`
- Từ chối request nếu score thấp hoặc action không khớp.

### Ưu / Nhược
- Ưu: UX tốt (ít challenge).
- Nhược: cần tinh chỉnh ngưỡng và quan sát false positive.

## 9.5 Khuyến nghị chọn phương án
- Giai đoạn đầu: triển khai v2 Checkbox để ổn định nhanh.
- Sau khi có telemetry: nâng cấp v3 cho UX nếu cần.

## 9.6 Ma trận môi trường
- `local/dev`:
  - `APP_SECURITY_RECAPTCHA_ENABLED=false` (hoặc dùng test key)
- `staging`:
  - `APP_SECURITY_RECAPTCHA_ENABLED=true`, theo dõi tỉ lệ fail
- `production`:
  - `APP_SECURITY_RECAPTCHA_ENABLED=true` (bắt buộc), giới hạn origin CORS chặt

---

## 10) Lỗi 400 khi bấm "Xác thực OTP": phân tích chi tiết và cách sửa

## 10.1 Các nguyên nhân phổ biến

1. OTP hết hạn trong Redis (TTL mặc định 5 phút).
   - Backend trả `400` với message kiểu: `OTP da het han hoac khong ton tai`.

2. OTP sai.
   - Backend trả `400` với message kiểu: `OTP khong dung`.

3. Body không hợp lệ theo validation (`email`, `otp`).
   - Ví dụ otp không đủ 6 số.
   - Trước đây có thể nhận payload lỗi khó đọc do chưa thống nhất format.

4. Email đầu vào lệch chuẩn (thừa khoảng trắng/chữ hoa/chữ thường không đồng nhất giữa các bước).
   - Có thể làm người dùng nhập đúng OTP nhưng backend lookup sai key ngữ cảnh.

## 10.2 Các thay đổi đã áp dụng để giảm 400 giả và tăng khả năng chẩn đoán

1. Chuẩn hóa email trước khi điều hướng sang màn verify:
   - [../../event-manager-fe/src/features/auth/components/SendOtpForm.tsx](../../event-manager-fe/src/features/auth/components/SendOtpForm.tsx)

2. Chuẩn hóa email khi gọi verify API (`trim + lowercase`):
   - [../../event-manager-fe/src/features/auth/api/verify-otp.ts](../../event-manager-fe/src/features/auth/api/verify-otp.ts)

3. Cập nhật schema response verify theo contract backend (status/registerToken/tokenType):
   - [../../event-manager-fe/src/features/auth/api/schemas.ts](../../event-manager-fe/src/features/auth/api/schemas.ts)

4. Thêm global validation handler để trả `{ "error": "..." }` rõ ràng cho lỗi binding/validation:
   - [../src/main/java/com/kietta/eventmanager/core/exception/GlobalExceptionHandler.java](../src/main/java/com/kietta/eventmanager/core/exception/GlobalExceptionHandler.java)

## 10.3 Cách xác định chính xác lỗi 400 bạn đang gặp

Mở tab Network ở frontend, chọn request `POST /api/v1/auth/verify-otp`, kiểm tra response body:

- Nếu `error = OTP da het han hoac khong ton tai`:
  - Gửi lại OTP và nhập trong vòng TTL.

- Nếu `error = OTP khong dung`:
  - Nhập lại OTP đúng 6 số mới nhất.

- Nếu `error` liên quan email/otp invalid:
  - Kiểm tra payload request có đúng format không.

## 10.4 Hướng cải tiến tiếp theo cho UX lỗi 400

1. Thêm nút `Gửi lại OTP` tại màn verify.
2. Hiển thị countdown TTL OTP.
3. Map mã lỗi backend (`code`) sang thông điệp UX rõ nghĩa hơn.
4. Khi gần hết hạn OTP, chủ động đề xuất resend.

---

## 11) Giải thích rõ cơ chế score của reCAPTCHA v3

## 11.1 Score là gì?
- reCAPTCHA v3 trả về điểm `score` trong khoảng từ `0.0` đến `1.0`.
- Điểm cao hơn nghĩa là hành vi giống người thật hơn.
- Điểm thấp hơn nghĩa là rủi ro bot cao hơn.

Lưu ý quan trọng: điểm không phải công thức cố định do phía ứng dụng tự tính. Điểm do Google mô hình hoá dựa trên nhiều tín hiệu hành vi và reputation.

## 11.2 Các tín hiệu thường ảnh hưởng score
- Tương tác người dùng trên trang (chuột, bàn phím, nhịp thao tác).
- Lịch sử/uy tín trình duyệt, IP, phiên làm việc.
- Mức độ bất thường của tần suất request.
- Sự phù hợp giữa `action` phía frontend và kết quả verify phía backend.

## 11.3 Cách backend ra quyết định từ score
Backend không dùng score trực tiếp để "cho phép tất cả" hay "chặn tất cả". Nên áp dụng policy theo ngưỡng:

- `score >= 0.7`: rủi ro thấp, cho qua.
- `0.3 <= score < 0.7`: rủi ro trung bình, cho qua có điều kiện (throttle/rate-limit mạnh hơn hoặc yêu cầu step-up).
- `score < 0.3`: rủi ro cao, từ chối hoặc bắt challenge mạnh hơn (ví dụ chuyển fallback v2 checkbox).

Ngưỡng chỉ là điểm khởi đầu, cần theo dõi log thực tế để tune.

## 11.4 Vì sao v3 khó hơn v2?
- v2 checkbox: quyết định theo challenge trực quan, dễ hiểu ngay.
- v3: quyết định dựa trên score xác suất, cần telemetry để tối ưu tránh:
  - false positive (chặn nhầm người dùng thật),
  - false negative (lọt bot).

## 11.5 Khi nào nên chọn v3
- Lưu lượng lớn, cần UX mượt, không muốn thêm bước click.
- Có khả năng theo dõi metric và tune ngưỡng theo thời gian.

## 11.6 Khuyến nghị triển khai thực tế
1. Bắt đầu từ v2 để ổn định nghiệp vụ OTP.
2. Thu thập metric abuse + tỷ lệ fail.
3. Rollout v3 từng phần (canary), có fallback sang v2 nếu score thấp.
