package com.kietta.eventmanager.domain.auth.service;

public interface NotificationService {
    void sendOtpCode(String recipient, String otpCode);
}
