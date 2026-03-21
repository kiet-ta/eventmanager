package com.kietta.eventmanager.domain.auth.controller;

import com.kietta.eventmanager.domain.auth.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final NotificationService notificationService;

    @PostMapping("/test-mail")
    public ResponseEntity<String> testMail(@RequestParam String recipient) {
        notificationService.sendHelloWorld(recipient);
        return ResponseEntity.ok("Mail sent to " + recipient);
    }
}
