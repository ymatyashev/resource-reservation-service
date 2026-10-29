package com.example.reservation.reservation.controller;

import com.example.reservation.reservation.dto.ReservationCreateRequest;
import com.example.reservation.reservation.dto.ReservationResponse;
import com.example.reservation.reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody ReservationCreateRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // Validate Idempotency-Key boundary conditions
        if (idempotencyKey != null) {
            idempotencyKey = idempotencyKey.trim();
            if (idempotencyKey.isEmpty()) {
                idempotencyKey = null; // Treat blank key as no key
            } else if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
                throw new com.example.reservation.common.exception.InvalidRequestException(
                        "Idempotency-Key must not exceed 255 characters");
            }
        }
        ReservationService.CreateResult result = reservationService.create(request, idempotencyKey);
        ReservationResponse response = result.reservation();
        URI location = URI.create("/api/v1/reservations/" + response.id());
        if (result.isReplay()) {
            // Return 200 OK for idempotent replays
            return ResponseEntity.ok().location(location).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> findByCustomerId(@RequestParam String customerId) {
        return ResponseEntity.ok(reservationService.findByCustomerId(customerId));
    }

    @PostMapping("/{id}/cancellation")
    public ResponseEntity<ReservationResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.cancel(id));
    }
}
