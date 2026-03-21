package com.kietta.eventmanager.domain.event.repository;

import com.kietta.eventmanager.domain.event.entity.Event;
import com.kietta.eventmanager.domain.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    
}
