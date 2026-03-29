package com.kietta.eventmanager.domain.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class RecaptchaService {
    @Value("${app.security.recaptcha.enabled:true}")
    private boolean recaptchaEnabled;

    @Value("${app.security.recaptcha.secret-key}")
    private String secretKey;

    @Value("${app.security.recaptcha.verify-url}")
    private String verifyUrl;
    // get the api external, using RestTemplate to call (HTTP Client)
    private final RestTemplate restTemplate = new RestTemplate();

    public void verifyToken(String token) {
        if (!recaptchaEnabled) {
            log.info("reCAPTCHA verification is disabled by configuration. Skipping verification.");
            return;
        }

        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Invalid Token reCAPTCHA!");
        }

        // Google reCAPTCHA API required data type is form-urlencoded (Not Json)
        // Using Map for flexible API required.
        MultiValueMap<String,String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("secret", secretKey);
        requestBody.add("response", token);

        try {
            // send request POST to GOOGLE
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    verifyUrl,
                    requestBody,
                    Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new IllegalArgumentException("Khong the xac minh reCAPTCHA luc nay.");
            }

            // check fields "SUCCESS" for Google return
            boolean isSuccess = (boolean) responseBody.getOrDefault("success", false);

            if (!isSuccess) {
                log.warn("Cảnh báo: Phát hiện bot hoặc token reCAPTCHA không hợp lệ! Response: {}", responseBody);
                throw new IllegalArgumentException("Xác thực reCAPTCHA thất bại! Vui lòng thử lại.");
            }

            log.info("reCAPTCHA hợp lệ. Cho phép đi tiếp!");
        } catch (Exception e) {
            log.error("Lỗi khi kết nối với máy chủ Google reCAPTCHA", e);
            throw new IllegalArgumentException("Không thể xác minh reCAPTCHA lúc này.");
        }
    }


}
