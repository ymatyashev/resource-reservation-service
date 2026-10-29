package com.example.reservation;

import com.example.reservation.resource.dto.ResourceCreateRequest;
import com.example.reservation.resource.dto.ResourceResponse;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ResourceControllerIntegrationTest extends DatabaseCleanup {

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.create("http://localhost:" + port);
    }

    @Test
    void createResource_withValidName_returns201() {
        ResourceCreateRequest request = new ResourceCreateRequest("Meeting Room A");
        ResponseEntity<ResourceResponse> response = restClient.post()
                .uri("/api/v1/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ResourceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        ResourceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.name()).isEqualTo("Meeting Room A");
        assertThat(body.active()).isTrue();
        assertThat(body.id()).isNotNull();
    }

    @Test
    void createResource_withBlankName_returns400() {
        try {
            restClient.post()
                    .uri("/api/v1/resources")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResourceCreateRequest("  "))
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Blank name returns HTTP 400")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void createResource_withNullName_returns400() {
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
                    .as("Null name returns HTTP 400")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void listResources_whenEmpty_returnsEmptyList() {
        ResponseEntity<List<ResourceResponse>> response = restClient.get()
                .uri("/api/v1/resources")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<ResourceResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void listResources_whenPopulated_returnsAllResources() {
        restClient.post()
                .uri("/api/v1/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ResourceCreateRequest("Test Room"))
                .retrieve()
                .toEntity(ResourceResponse.class);

        ResponseEntity<List<ResourceResponse>> response = restClient.get()
                .uri("/api/v1/resources")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<ResourceResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).name()).isEqualTo("Test Room");
    }

    @Test
    void getResourceById_exists_returns200() {
        ResourceCreateRequest request = new ResourceCreateRequest("Room to Find");
        ResponseEntity<ResourceResponse> createResponse = restClient.post()
                .uri("/api/v1/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(ResourceResponse.class);
        UUID id = createResponse.getBody().id();

        ResponseEntity<ResourceResponse> response = restClient.get()
                .uri("/api/v1/resources/" + id)
                .retrieve()
                .toEntity(ResourceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(id);
        assertThat(response.getBody().name()).isEqualTo("Room to Find");
    }

    @Test
    void getResourceById_notFound_returns404() {
        UUID nonExistentId = UUID.randomUUID();
        try {
            restClient.get()
                    .uri("/api/v1/resources/" + nonExistentId)
                    .retrieve()
                    .toEntity(String.class);
            assertThat(false).as("Expected HttpClientErrorException").isTrue();
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode())
                    .as("Non-existent resource returns HTTP 404")
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
