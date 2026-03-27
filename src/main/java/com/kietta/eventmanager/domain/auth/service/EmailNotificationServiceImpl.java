package com.kietta.eventmanager.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailNotificationServiceImpl implements NotificationService {
    // import library send mail, it like a communication to connect mail and java
    // target using it to call mail method using for sender and recipient
    private final JavaMailSender mailSender;

    @Override
    public void sendOtpCode(String recipient, String otpCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        // Simple Mail message including TO: recipient SUBJECT: text  MESSAGE: text FROM: sender
        message.setTo(recipient);
        message.setSubject("Ma OTP dang nhap Event Manager");
        message.setText("Ma OTP cua ban la: " + otpCode + ". Ma co hieu luc trong 5 phut.");
        mailSender.send(message);
    }

}
