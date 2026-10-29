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
class ConcurrencyIntegrationTest extends DatabaseCleanup {

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

    @Test
    void concurrentReservationRequests_onlyOneSucceeds() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        ReservationCreateRequest request = new ReservationCreateRequest(
                resourceId, "customer-1",
                Instant.now().plus(Duration.ofDays(1)),
                Instant.now().plus(Duration.ofDays(1)).plus(Duration.ofHours(1)));

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await();
                    ResponseEntity<ReservationResponse> response = restClient.post()
                            .uri("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .toEntity(ReservationResponse.class);

                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else {
                        otherErrorCount.incrementAndGet();
                    }
                } catch (HttpClientErrorException e) {
                    if (e.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        otherErrorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
            threads.add(thread);
            executor.submit(thread);
        }

        // Start all threads at once
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Exactly one should succeed with 201 Created
        assertThat(successCount.get())
                .as("Exactly one reservation should succeed")
                .isEqualTo(1);

        // All others should get 409 Conflict
        assertThat(conflictCount.get())
                .as("All other requests should get 409 Conflict")
                .isEqualTo(threadCount - 1);

        // No unexpected errors
        assertThat(otherErrorCount.get())
                .as("No unexpected errors should occur")
                .isZero();

        // Verify database state: exactly one confirmed reservation
        Integer confirmedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE status = 'CONFIRMED'", Integer.class);
        assertThat(confirmedCount)
                .as("Exactly one confirmed reservation in the database")
                .isEqualTo(1);
    }
}
