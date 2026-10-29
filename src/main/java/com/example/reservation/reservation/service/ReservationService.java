package com.example.reservation.reservation.service;

import com.example.reservation.common.exception.*;
import com.example.reservation.reservation.dto.ReservationCreateRequest;
import com.example.reservation.reservation.dto.ReservationResponse;
import com.example.reservation.reservation.entity.IdempotencyKey;
import com.example.reservation.reservation.entity.Reservation;
import com.example.reservation.reservation.repository.IdempotencyKeyRepository;
import com.example.reservation.reservation.repository.ReservationRepository;
import com.example.reservation.resource.entity.Resource;
import com.example.reservation.resource.service.ResourceService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    private final ResourceService resourceService;
    private final ReservationRepository reservationRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public ReservationService(ResourceService resourceService,
                              ReservationRepository reservationRepository,
                              IdempotencyKeyRepository idempotencyKeyRepository) {
        this.resourceService = resourceService;
        this.reservationRepository = reservationRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    public record CreateResult(ReservationResponse reservation, boolean isReplay) {}

    @Transactional
    public CreateResult create(ReservationCreateRequest request, String idempotencyKey) {
        String requestHash = computeRequestHash(request);

        // Lock the resource row first to serialize all reservation creation for this resource
        Resource resource = resourceService.getByIdEntityForUpdate(request.getResourceId());
        if (!resource.isActive()) {
            throw new ResourceNotFoundException("Resource not found or inactive: " + request.getResourceId());
        }

        // Check idempotency key immediately after acquiring the resource lock, before any business validation
        // This ensures a retry with the same key returns the original result even if the start time has passed
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IdempotencyKey existing = idempotencyKeyRepository.findByKey(idempotencyKey).orElse(null);
            if (existing != null) {
                if (!existing.getRequestHash().equals(requestHash)) {
                    throw new IdempotencyConflictException(
                            "Idempotency key already used with different request: " + idempotencyKey);
                }
                // Return existing reservation as a replay
                Reservation reservation = reservationRepository.findById(existing.getReservation().getId())
                        .orElseThrow(() -> new ReservationNotFoundException(
                                "Reservation not found for idempotency key: " + idempotencyKey));
                return new CreateResult(toResponse(reservation), true);
            }
        }

        // Validate start < end (only for new reservations)
        if (!request.getStart().isBefore(request.getEnd())) {
            throw new InvalidRequestException("Start time must be before end time");
        }

        // Validate start time is not in the past (only for new reservations)
        Instant now = Instant.now();
        if (request.getStart().isBefore(now)) {
            throw new InvalidRequestException("Start time must not be in the past");
        }

        // Check for overlapping confirmed reservations
        List<Reservation> overlapping = reservationRepository.findConfirmedByResourceIdAndOverlapping(
                request.getResourceId(), request.getStart(), request.getEnd());
        if (!overlapping.isEmpty()) {
            throw new OverlappingReservationException(
                    "Overlapping reservation exists for resource: " + request.getResourceId());
        }

        // Create reservation
        Reservation reservation = new Reservation(
                resource, request.getCustomerId(), request.getStart(), request.getEnd());
        Reservation saved = reservationRepository.save(reservation);

        // Save idempotency key atomically with reservation
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                IdempotencyKey key = new IdempotencyKey(idempotencyKey, saved, requestHash);
                idempotencyKeyRepository.save(key);
            } catch (DataIntegrityViolationException e) {
                // Concurrent request with same key won the race; return its reservation as a replay
                IdempotencyKey existing = idempotencyKeyRepository.findByKey(idempotencyKey).orElse(null);
                if (existing != null) {
                    Reservation existingReservation = reservationRepository.findById(existing.getReservation().getId())
                            .orElseThrow(() -> new ReservationNotFoundException(
                                    "Reservation not found for idempotency key: " + idempotencyKey));
                    return new CreateResult(toResponse(existingReservation), true);
                }
                throw new IdempotencyConflictException(
                        "Idempotency key conflict: " + idempotencyKey);
            }
        }

        return new CreateResult(toResponse(saved), false);
    }

    private String computeRequestHash(ReservationCreateRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = request.getResourceId() + "|" +
                    request.getCustomerId() + "|" +
                    request.getStart().toString() + "|" +
                    request.getEnd().toString();
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getById(UUID id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found with id: " + id));
        return toResponse(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> findByCustomerId(String customerId) {
        return reservationRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ReservationResponse cancel(UUID id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Reservation not found with id: " + id));
        reservation.cancel();
        reservationRepository.save(reservation);
        return toResponse(reservation);
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getResource().getId(),
                reservation.getCustomerId(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.getCreatedAt()
        );
    }
}
