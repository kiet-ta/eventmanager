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
    public void sendHelloWorld(String recipient, String mess) {
        // declare message format
        SimpleMailMessage message = new SimpleMailMessage();
        // Simple Mail message including TO: recipient SUBJECT: text  MESSAGE: text FROM: sender
        message.setTo(recipient);
        message.setSubject("Hello từ Hệ Thống Săn Vé Fan Meeting");
        message.setText("Mã OTP của bạn là: " + mess + ". Vui lòng sử dụng mã này để xác thực tài khoản. Mã có hiệu lực trong 5 phút. Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email này.");

        //information full so we will send
        mailSender.send(message);
    }

}
