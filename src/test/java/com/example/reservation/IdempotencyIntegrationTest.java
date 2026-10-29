package com.example.reservation;

import com.example.reservation.resource.dto.ResourceCreateRequest;
import com.example.reservation.resource.dto.ResourceResponse;
import com.example.reservation.reservation.dto.ReservationCreateRequest;
import com.example.reservation.reservation.dto.ReservationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdempotencyIntegrationTest extends DatabaseCleanup {

    @LocalServerPort
    private int port;

    private RestClient restClient;
    private UUID resourceId;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        restClient = RestClient.create("http://localhost:" + port);

        ResourceResponse resource = restClient.post()
                .uri("/api/v1/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ResourceCreateRequest("Test Room"))
                .retrieve()
                .toEntity(ResourceResponse.class)
                .getBody();
        resourceId = resource.id();
    }

    private Instant futureStart() {
        return Instant.now().plus(Duration.ofDays(1));
    }

    private Instant futureEnd() {
        return Instant.now().plus(Duration.ofDays(1)).plus(Duration.ofHours(1));
    }

    @Test
    void sameKey_sameRequest_returnsOriginal() {
        ReservationCreateRequest request = new ReservationCreateRequest(
                resourceId, "customer-1",
                futureStart(), futureEnd());

        ResponseEntity<ReservationResponse> first = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "key-1")
                .body(request)
                .retrieve()
                .toEntity(ReservationResponse.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ReservationResponse> second = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "key-1")
                .body(request)
                .retrieve()
                .toEntity(ReservationResponse.class);

        assertThat(second.getStatusCode())
                .as("Idempotent replay returns HTTP 200 OK")
                .isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());
    }

    @Test
    void retryAfterStartTime_returns200() throws InterruptedException {
        // Create a reservation with a start time very slightly in the future (2 seconds from now)
        Instant start = Instant.now().plus(Duration.ofSeconds(2));
        Instant end = start.plus(Duration.ofHours(1));
        ReservationCreateRequest request = new ReservationCreateRequest(
                resourceId, "customer-1", start, end);

        // First request creates the reservation
        ResponseEntity<ReservationResponse> first = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "retry-after-start")
                .body(request)
                .retrieve()
                .toEntity(ReservationResponse.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Wait until the start time has passed
        Thread.sleep(3000);

        // Retry with the same key — should return 200 OK, not 400 (start time in past)
        ResponseEntity<ReservationResponse> second = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "retry-after-start")
                .body(request)
                .retrieve()
                .toEntity(ReservationResponse.class);

        assertThat(second.getStatusCode())
                .as("Retry after start time passes returns HTTP 200 OK, not 400")
                .isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());
    }

    @Test
    void sameKey_differentRequest_returns409() {
        ReservationCreateRequest request1 = new ReservationCreateRequest(
                resourceId, "customer-1",
                futureStart(), futureEnd());

        restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "key-2")
                .body(request1)
                .retrieve()
                .toEntity(ReservationResponse.class);

        ReservationCreateRequest request2 = new ReservationCreateRequest(
                resourceId, "customer-2",
                futureEnd(), futureEnd().plus(Duration.ofHours(1)));

        try {
            restClient.post()
                    .uri("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "key-2")
                    .body(request2)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Same key with different request returns HTTP 409")
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    void noKey_normalCreation() {
        ReservationCreateRequest request = new ReservationCreateRequest(
                resourceId, "customer-1",
                futureStart(), futureEnd());

        ResponseEntity<ReservationResponse> response = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ReservationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void concurrentSameKey_onlyOneReservationCreated() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        ReservationCreateRequest request = new ReservationCreateRequest(
                resourceId, "customer-1",
                futureStart(), futureEnd());

        String idempotencyKey = "concurrent-key-1";
        List<UUID> reservationIds = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await();
                    ResponseEntity<ReservationResponse> response = restClient.post()
                            .uri("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", idempotencyKey)
                            .body(request)
                            .retrieve()
                            .toEntity(ReservationResponse.class);

                    if (response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                        synchronized (reservationIds) {
                            reservationIds.add(response.getBody().id());
                        }
                    } else {
                        otherErrorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
            executor.submit(thread);
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // All requests should succeed (either 201 or 200 with the same reservation)
        assertThat(successCount.get())
                .as("All concurrent requests with same idempotency key should succeed")
                .isEqualTo(threadCount);

        // No unexpected errors
        assertThat(otherErrorCount.get())
                .as("No unexpected errors should occur")
                .isZero();

        // All responses should return the same reservation ID
        assertThat(reservationIds)
                .as("All responses should return the same reservation ID")
                .isNotEmpty()
                .containsOnly(reservationIds.get(0));

        // Verify database state: exactly one confirmed reservation
        Integer confirmedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE status = 'CONFIRMED'", Integer.class);
        assertThat(confirmedCount)
                .as("Exactly one confirmed reservation in the database")
                .isEqualTo(1);
    }
}
