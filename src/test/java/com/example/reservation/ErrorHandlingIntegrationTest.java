package com.example.reservation;

import com.example.reservation.resource.dto.ResourceCreateRequest;
import com.example.reservation.resource.dto.ResourceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorHandlingIntegrationTest extends DatabaseCleanup {

    @LocalServerPort
    private int port;

    @Test
    void validationError_returns400WithProblemDetail() {
        RestClient restClient = RestClient.create("http://localhost:" + port);

        try {
            restClient.post()
                    .uri("/api/v1/resources")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResourceCreateRequest())
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Validation error returns HTTP 400")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            String body = ex.getResponseBodyAsString();
            assertThat(body)
                    .as("Response contains ProblemDetail type field")
                    .contains("\"type\"");
            assertThat(body)
                    .as("Response contains ProblemDetail title field")
                    .contains("\"title\"");
            assertThat(body)
                    .as("Response contains ProblemDetail status field")
                    .contains("\"status\"");
            assertThat(body)
                    .as("Response contains ProblemDetail detail field")
                    .contains("\"detail\"");
        }
    }

    @Test
    void notFound_returns404WithProblemDetail() {
        RestClient restClient = RestClient.create("http://localhost:" + port);

        try {
            restClient.get()
                    .uri("/api/v1/resources/" + "00000000-0000-0000-0000-000000000000")
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Not found returns HTTP 404")
                    .isEqualTo(HttpStatus.NOT_FOUND);
            String body = ex.getResponseBodyAsString();
            assertThat(body)
                    .as("Response contains ProblemDetail type field")
                    .contains("\"type\"");
            assertThat(body)
                    .as("Response contains ProblemDetail title field")
                    .contains("\"title\"");
            assertThat(body)
                    .as("Response contains ProblemDetail status field")
                    .contains("\"status\"");
            assertThat(body)
                    .as("Response contains ProblemDetail detail field")
                    .contains("\"detail\"");
        }
    }

    @Test
    void overlappingReservation_returns409WithProblemDetail() {
        RestClient restClient = RestClient.create("http://localhost:" + port);

        // Create a resource
        String resourceJson = "{\"name\":\"Room A\"}";
        ResponseEntity<ResourceResponse> resourceResponse = restClient.post()
                .uri("/api/v1/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .body(resourceJson)
                .retrieve()
                .toEntity(ResourceResponse.class);
        String resourceId = resourceResponse.getBody().id().toString();

        // Create first reservation
        Instant start = Instant.now().plus(Duration.ofDays(1));
        Instant end = start.plus(Duration.ofHours(1));
        String resJson = "{\"resourceId\":\"" + resourceId + "\",\"customerId\":\"customer-1\",\"start\":\"" + start + "\",\"end\":\"" + end + "\"}";
        restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(resJson)
                .retrieve()
                .toEntity(String.class);

        // Attempt overlapping reservation
        Instant overlapStart = start.plus(Duration.ofMinutes(30));
        Instant overlapEnd = end.plus(Duration.ofMinutes(30));
        String overlapJson = "{\"resourceId\":\"" + resourceId + "\",\"customerId\":\"customer-2\",\"start\":\"" + overlapStart + "\",\"end\":\"" + overlapEnd + "\"}";
        try {
            restClient.post()
                    .uri("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(overlapJson)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Overlapping reservation returns HTTP 409")
                    .isEqualTo(HttpStatus.CONFLICT);
            String body = ex.getResponseBodyAsString();
            assertThat(body)
                    .as("Response contains ProblemDetail type field")
                    .contains("\"type\"");
            assertThat(body)
                    .as("Response contains ProblemDetail title field")
                    .contains("\"title\"");
        }
    }

    @Test
    void malformedJson_returns400WithProblemDetail() {
        RestClient restClient = RestClient.create("http://localhost:" + port);

        try {
            restClient.post()
                    .uri("/api/v1/resources")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{invalid json}")
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Malformed JSON returns HTTP 400")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            String body = ex.getResponseBodyAsString();
            assertThat(body)
                    .as("Response contains ProblemDetail detail field")
                    .contains("\"detail\"");
        }
    }

    @Test
    void invalidUuid_returns400WithProblemDetail() {
        RestClient restClient = RestClient.create("http://localhost:" + port);

        try {
            restClient.get()
                    .uri("/api/v1/resources/not-a-uuid")
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Invalid UUID returns HTTP 400")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            String body = ex.getResponseBodyAsString();
            assertThat(body)
                    .as("Response contains ProblemDetail detail field")
                    .contains("\"detail\"");
        }
    }

    @Test
    void invalidTimestamp_returns400WithProblemDetail() {
        RestClient restClient = RestClient.create("http://localhost:" + port);

        // Create a resource first
        String resourceJson = "{\"name\":\"Room A\"}";
        ResponseEntity<ResourceResponse> resourceResponse = restClient.post()
                .uri("/api/v1/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .body(resourceJson)
                .retrieve()
                .toEntity(ResourceResponse.class);
        String resourceId = resourceResponse.getBody().id().toString();

        // Send reservation with invalid timestamp
        String resJson = "{\"resourceId\":\"" + resourceId + "\",\"customerId\":\"customer-1\",\"start\":\"not-a-date\",\"end\":\"2026-12-01T10:00:00Z\"}";
        try {
            restClient.post()
                    .uri("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resJson)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Invalid timestamp returns HTTP 400")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            String body = ex.getResponseBodyAsString();
            assertThat(body)
                    .as("Response contains ProblemDetail detail field")
                    .contains("\"detail\"");
        }
    }

    @Test
    void oversizedIdempotencyKey_returns400() {
        RestClient restClient = RestClient.create("http://localhost:" + port);

        // Create a resource first
        String resourceJson = "{\"name\":\"Room A\"}";
        ResponseEntity<ResourceResponse> resourceResponse = restClient.post()
                .uri("/api/v1/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .body(resourceJson)
                .retrieve()
                .toEntity(ResourceResponse.class);
        String resourceId = resourceResponse.getBody().id().toString();

        // Create oversized key (256 characters)
        String oversizedKey = "a".repeat(256);
        Instant start = Instant.now().plus(Duration.ofDays(1));
        Instant end = start.plus(Duration.ofHours(1));
        String resJson = "{\"resourceId\":\"" + resourceId + "\",\"customerId\":\"customer-1\",\"start\":\"" + start + "\",\"end\":\"" + end + "\"}";

        try {
            restClient.post()
                    .uri("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", oversizedKey)
                    .body(resJson)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Oversized Idempotency-Key returns HTTP 400")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void blankIdempotencyKey_treatedAsNoKey() {
        RestClient restClient = RestClient.create("http://localhost:" + port);

        // Create a resource first
        String resourceJson = "{\"name\":\"Room A\"}";
        ResponseEntity<ResourceResponse> resourceResponse = restClient.post()
                .uri("/api/v1/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .body(resourceJson)
                .retrieve()
                .toEntity(ResourceResponse.class);
        String resourceId = resourceResponse.getBody().id().toString();

        // Send with blank idempotency key — should create normally
        Instant start = Instant.now().plus(Duration.ofDays(1));
        Instant end = start.plus(Duration.ofHours(1));
        String resJson = "{\"resourceId\":\"" + resourceId + "\",\"customerId\":\"customer-1\",\"start\":\"" + start + "\",\"end\":\"" + end + "\"}";

        ResponseEntity<String> response = restClient.post()
                .uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "   ")
                .body(resJson)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode())
                .as("Blank Idempotency-Key is treated as no key, returns 201")
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void missingCustomerId_returns400WithProblemDetail() {
        RestClient restClient = RestClient.create("http://localhost:" + port);

        try {
            restClient.get()
                    .uri("/api/v1/reservations")
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Missing customerId returns HTTP 400")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            String body = ex.getResponseBodyAsString();
            assertThat(body)
                    .as("Response contains ProblemDetail detail field")
                    .contains("\"detail\"");
        }
    }
}
