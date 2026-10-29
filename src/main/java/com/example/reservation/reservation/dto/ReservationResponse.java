package com.example.reservation.reservation.dto;

import com.example.reservation.reservation.entity.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID resourceId,
        String customerId,
        Instant startTime,
        Instant endTime,
        ReservationStatus status,
        Instant createdAt
) {}
