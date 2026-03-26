package com.kietta.eventmanager.domain.user.entity;

import com.kietta.eventmanager.core.constant.IdentityUserType;
import com.kietta.eventmanager.core.constant.UserRole;
import com.kietta.eventmanager.core.constant.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter
    @Column(nullable = false, length = 50)
    private String firstName;

    @Setter
    @Column(nullable = false, length = 50)
    private String lastName;

    @Setter
    @Column(unique = true, nullable = false, length = 50)
    private String identityNumber;

    @Enumerated(EnumType.STRING)
    @Setter
    @Column(length = 20)
    private IdentityUserType identityType = IdentityUserType.CCCD; // CCCD/PASSPORT

    @Setter
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
