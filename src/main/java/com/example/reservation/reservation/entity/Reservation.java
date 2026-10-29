package com.example.reservation.reservation.entity;

import com.example.reservation.resource.entity.Resource;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Column(name = "customer_id", nullable = false, length = 255)
    private String customerId;

    @Column(name = "start_time", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant startTime;

    @Column(name = "end_time", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    public Reservation() {
    }

    public Reservation(Resource resource, String customerId, Instant startTime, Instant endTime) {
        this.resource = resource;
        this.customerId = customerId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Resource getResource() {
        return resource;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }
}
