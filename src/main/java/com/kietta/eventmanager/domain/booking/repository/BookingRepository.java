package com.kietta.eventmanager.domain.booking.repository;

import com.kietta.eventmanager.domain.booking.entity.Booking;
import com.kietta.eventmanager.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

}
