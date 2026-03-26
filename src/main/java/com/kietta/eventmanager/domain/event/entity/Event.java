package com.kietta.eventmanager.domain.event.entity;

import com.kietta.eventmanager.core.constant.EventStatus;
import com.kietta.eventmanager.domain.user.entity.User;
import com.kietta.eventmanager.domain.venue.entity.Venue;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue
    private UUID id;

    @Setter
    @Column(nullable = false)
    private String title;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String description;

    // Many events -> 1 venue
    @ManyToOne(fetch = FetchType.LAZY)
    private Venue venue;

    // Many events -> 1 user (organizer)
    @ManyToOne(fetch = FetchType.LAZY)
    private User organizer;

    @Setter
    @Column(nullable = false)
    private Instant eventDate;

    @Setter
    @Column(nullable = false)
    private Instant openSaleTime;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EventStatus status = EventStatus.DRAFT;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
