package com.kietta.eventmanager.domain.user_identities.entity;

import com.kietta.eventmanager.core.constant.TicketStatus;
import com.kietta.eventmanager.domain.booking.entity.Booking;
import com.kietta.eventmanager.domain.event.entity.Event;
import com.kietta.eventmanager.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "user_identities")
@NoArgsConstructor
public class UserIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    @Setter
    private User user;

    // LOCAL (Email/Pass), GOOGLE, FACEBOOK
    @Column(nullable = false)
    @Setter
    private String provider;

    @Column(nullable = false, unique = true)
    @Setter
    private String providerId;

    @Setter
    private String passwordHash;

    @Setter
    private boolean isVerified = false;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

}
