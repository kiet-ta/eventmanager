package com.kietta.eventmanager.domain.user.repository;

import com.kietta.eventmanager.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>{
}
