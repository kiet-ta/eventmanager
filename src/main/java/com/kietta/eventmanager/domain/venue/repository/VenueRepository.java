package com.kietta.eventmanager.domain.venue.repository;

import com.kietta.eventmanager.domain.ticket.entity.Ticket;
import com.kietta.eventmanager.domain.venue.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, Integer> {
    
}
