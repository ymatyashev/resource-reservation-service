package com.example.reservation.resource.dto;

import java.time.Instant;
import java.util.UUID;

public record ResourceResponse(
        UUID id,
        String name,
        boolean active,
        Instant createdAt
) {
}
