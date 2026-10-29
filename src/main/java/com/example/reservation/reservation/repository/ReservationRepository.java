package com.example.reservation.reservation.repository;

import com.example.reservation.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByCustomerId(String customerId);

    @Query("SELECT r FROM Reservation r WHERE r.resource.id = :resourceId " +
            "AND r.status = 'CONFIRMED' " +
            "AND r.startTime < :endTime AND r.endTime > :startTime")
    List<Reservation> findConfirmedByResourceIdAndOverlapping(
            @Param("resourceId") UUID resourceId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);
}
