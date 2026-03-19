package com.kietta.eventmanager.domain.user.entity;

import com.kietta.eventmanager.core.constant.userRole;
import com.kietta.eventmanager.core.constant.userStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;


@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID eventId;

    @Setter
    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 200)
    private  String passwordHash;

    @Setter
    @Column(nullable = false, length = 50)
    private String firstName;

    @Setter
    @Column(nullable = false, length = 50)
    private String lastName;

    @Setter
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private userRole role = userRole.USER;

    @Enumerated(EnumType.STRING)
    private userStatus status = userStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
