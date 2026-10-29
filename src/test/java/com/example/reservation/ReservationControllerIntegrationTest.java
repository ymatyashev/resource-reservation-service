package com.example.reservation;

import com.example.reservation.resource.dto.ResourceCreateRequest;
import com.example.reservation.resource.dto.ResourceResponse;
import com.example.reservation.reservation.dto.ReservationCreateRequest;
import com.example.reservation.reservation.dto.ReservationResponse;
import com.example.reservation.reservation.entity.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReservationControllerIntegrationTest extends DatabaseCleanup {

    @LocalServerPort
    private int port;

    private RestClient restClient;
    private UUID resourceId;

    @BeforeEach
    void setUp() {
        restClient = RestClient.create("http://localhost:" + port);

        // Create a resource for tests
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
    void createReservation_valid_returns201() {
        Instant start = futureStart();
        Instant end = futureEnd();
        ReservationCreateRequest request = new ReservationCreateRequest(
                resourceId, "customer-1", start, end);

        ResponseEntity<ReservationResponse> response = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ReservationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().resourceId()).isEqualTo(resourceId);
        assertThat(response.getBody().status()).isEqualTo(ReservationStatus.CONFIRMED);
        // Verify Location header contains the reservation ID
        assertThat(response.getHeaders().getLocation())
                .as("Location header contains reservation ID")
                .isNotNull()
                .toString()
                .contains(response.getBody().id().toString());
    }

    @Test
    void createReservation_startAfterEnd_returns400() {
        Instant start = futureEnd();
        Instant end = futureStart();
        ReservationCreateRequest request = new ReservationCreateRequest(
                resourceId, "customer-1", start, end);

        try {
            restClient.post()
                    .uri("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Start after end returns HTTP 400")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void createReservation_nonExistentResource_returns404() {
        ReservationCreateRequest request = new ReservationCreateRequest(
                UUID.randomUUID(), "customer-1",
                futureStart(), futureEnd());

        try {
            restClient.post()
                    .uri("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Non-existent resource returns HTTP 404")
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void createReservation_overlapping_returns409() {
        Instant start1 = futureStart();
        Instant end1 = futureEnd();
        ReservationCreateRequest request1 = new ReservationCreateRequest(
                resourceId, "customer-1", start1, end1);
        restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request1)
                .retrieve()
                .toEntity(ReservationResponse.class);

        Instant start2 = start1.plus(Duration.ofMinutes(30));
        Instant end2 = end1.plus(Duration.ofMinutes(30));
        ReservationCreateRequest request2 = new ReservationCreateRequest(
                resourceId, "customer-2", start2, end2);

        try {
            restClient.post()
                    .uri("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request2)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Overlapping reservation returns HTTP 409")
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    void createReservation_fullyContained_returns409() {
        Instant start1 = futureStart();
        Instant end1 = start1.plus(Duration.ofHours(2));
        ReservationCreateRequest request1 = new ReservationCreateRequest(
                resourceId, "customer-1", start1, end1);
        restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request1)
                .retrieve()
                .toEntity(ReservationResponse.class);

        Instant start2 = start1.plus(Duration.ofMinutes(30));
        Instant end2 = start1.plus(Duration.ofMinutes(90));
        ReservationCreateRequest request2 = new ReservationCreateRequest(
                resourceId, "customer-2", start2, end2);

        try {
            restClient.post()
                    .uri("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request2)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Fully contained reservation returns HTTP 409")
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    void createReservation_identicalInterval_returns409() {
        Instant start = futureStart();
        Instant end = futureEnd();
        ReservationCreateRequest request1 = new ReservationCreateRequest(
                resourceId, "customer-1", start, end);
        restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request1)
                .retrieve()
                .toEntity(ReservationResponse.class);

        ReservationCreateRequest request2 = new ReservationCreateRequest(
                resourceId, "customer-2", start, end);

        try {
            restClient.post()
                    .uri("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request2)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Identical interval returns HTTP 409")
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    void differentResources_sameTime_bothSucceed() {
        ResourceResponse resource2 = restClient.post()
                .uri("/api/v1/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ResourceCreateRequest("Test Room 2"))
                .retrieve()
                .toEntity(ResourceResponse.class)
                .getBody();

        Instant start = futureStart();
        Instant end = futureEnd();
        ReservationCreateRequest request1 = new ReservationCreateRequest(
                resourceId, "customer-1", start, end);
        ResponseEntity<ReservationResponse> response1 = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request1)
                .retrieve()
                .toEntity(ReservationResponse.class);

        ReservationCreateRequest request2 = new ReservationCreateRequest(
                resource2.id(), "customer-2", start, end);
        ResponseEntity<ReservationResponse> response2 = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request2)
                .retrieve()
                .toEntity(ReservationResponse.class);

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void createReservation_adjacent_allowed() {
        Instant start1 = futureStart();
        Instant end1 = futureEnd();
        ReservationCreateRequest request1 = new ReservationCreateRequest(
                resourceId, "customer-1", start1, end1);
        restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request1)
                .retrieve()
                .toEntity(ReservationResponse.class);

        ReservationCreateRequest request2 = new ReservationCreateRequest(
                resourceId, "customer-2", end1, end1.plus(Duration.ofHours(1)));

        ResponseEntity<ReservationResponse> response = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request2)
                .retrieve()
                .toEntity(ReservationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void cancelReservation_confirmed_returnsCancelled() {
        ReservationCreateRequest request = new ReservationCreateRequest(
                resourceId, "customer-1", futureStart(), futureEnd());
        ReservationResponse created = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ReservationResponse.class)
                .getBody();

        ReservationResponse cancelled = restClient.post()
                .uri("/api/v1/reservations/" + created.id() + "/cancellation")
                .retrieve()
                .toEntity(ReservationResponse.class)
                .getBody();

        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelReservation_alreadyCancelled_isIdempotent() {
        ReservationCreateRequest request = new ReservationCreateRequest(
                resourceId, "customer-1", futureStart(), futureEnd());
        ReservationResponse created = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ReservationResponse.class)
                .getBody();

        restClient.post()
                .uri("/api/v1/reservations/" + created.id() + "/cancellation")
                .retrieve()
                .toEntity(ReservationResponse.class);

        ResponseEntity<ReservationResponse> response = restClient.post()
                .uri("/api/v1/reservations/" + created.id() + "/cancellation")
                .retrieve()
                .toEntity(ReservationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void cancelReservation_nonExistent_returns404() {
        try {
            restClient.post()
                    .uri("/api/v1/reservations/" + UUID.randomUUID() + "/cancellation")
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Cancel non-existent reservation returns HTTP 404")
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void reserveAfterCancellation_allowed() {
        Instant start = futureStart();
        Instant end = futureEnd();
        ReservationCreateRequest request = new ReservationCreateRequest(
                resourceId, "customer-1", start, end);
        ReservationResponse created = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ReservationResponse.class)
                .getBody();

        // Cancel
        restClient.post()
                .uri("/api/v1/reservations/" + created.id() + "/cancellation")
                .retrieve()
                .toEntity(ReservationResponse.class);

        // Re-reserve same slot
        ReservationCreateRequest request2 = new ReservationCreateRequest(
                resourceId, "customer-2", start, end);

        ResponseEntity<ReservationResponse> response = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request2)
                .retrieve()
                .toEntity(ReservationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void findByCustomerId_returnsReservations() {
        ReservationCreateRequest request = new ReservationCreateRequest(
                resourceId, "customer-1", futureStart(), futureEnd());
        restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ReservationResponse.class);

        ResponseEntity<List<ReservationResponse>> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/reservations")
                        .queryParam("customerId", "customer-1").build())
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<ReservationResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).customerId()).isEqualTo("customer-1");
    }

    @Test
    void createReservation_startTimeInPast_returns400() {
        ReservationCreateRequest request = new ReservationCreateRequest(
                resourceId, "customer-1",
                Instant.now().minus(Duration.ofHours(1)),
                Instant.now().minus(Duration.ofMinutes(30)));

        try {
            restClient.post()
                    .uri("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Start time in past returns HTTP 400")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void createReservation_startEqualsEnd_returns400() {
        Instant sameTime = futureStart();
        ReservationCreateRequest request = new ReservationCreateRequest(
                resourceId, "customer-1", sameTime, sameTime);

        try {
            restClient.post()
                    .uri("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Start time equals end time returns HTTP 400")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
