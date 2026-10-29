package com.example.reservation.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class ReservationCreateRequest {

    @NotNull(message = "Resource ID must not be null")
    private UUID resourceId;

    @NotBlank(message = "Customer ID must not be blank")
    @Size(max = 255, message = "Customer ID must not exceed 255 characters")
    private String customerId;

    @NotNull(message = "Start time must not be null")
    private Instant start;

    @NotNull(message = "End time must not be null")
    private Instant end;

    public ReservationCreateRequest() {
    }

    public ReservationCreateRequest(UUID resourceId, String customerId, Instant start, Instant end) {
        this.resourceId = resourceId;
        this.customerId = customerId;
        this.start = start;
        this.end = end;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }
}
