package com.kietta.eventmanager.domain.ticket.repository;

import com.kietta.eventmanager.domain.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

}
