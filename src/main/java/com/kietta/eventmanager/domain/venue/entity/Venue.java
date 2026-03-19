package com.kietta.eventmanager.domain.venue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
public class Venue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Setter
    @Column(nullable = false)
    private String name;

    @Setter
    @Column(nullable = false, columnDefinition = "TEXT")
    private String city;


    @Setter
    @Column(nullable = false, columnDefinition = "TEXT")
    private String district;


    @Setter
    @Column(nullable = false, columnDefinition = "TEXT")
    private String ward;


    @Setter
    @Column(nullable = false, columnDefinition = "TEXT")
    private String street;


    @Setter
    @Column(nullable = false)
    private Integer capacity;

    @Setter
    @Column( columnDefinition = "TEXT")
    private String layoutImageUrl;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
