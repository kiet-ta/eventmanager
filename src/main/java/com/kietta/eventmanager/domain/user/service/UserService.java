package com.kietta.eventmanager.domain.user.service;

import com.kietta.eventmanager.domain.user.entity.User;
import com.kietta.eventmanager.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service

@RequiredArgsConstructor
public class UserService {
    private  final UserRepository userRepository;



}
