package com.example.reservation.reservation.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(name = "key", length = 255)
    private String key;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    public IdempotencyKey() {
    }

    public IdempotencyKey(String key, Reservation reservation, String requestHash) {
        this.key = key;
        this.reservation = reservation;
        this.requestHash = requestHash;
    }

    public String getKey() {
        return key;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public String getRequestHash() {
        return requestHash;
    }
}
