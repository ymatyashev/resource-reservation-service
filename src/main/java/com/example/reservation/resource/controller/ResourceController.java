package com.example.reservation.resource.controller;

import com.example.reservation.resource.dto.ResourceCreateRequest;
import com.example.reservation.resource.dto.ResourceResponse;
import com.example.reservation.resource.service.ResourceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping
    public ResponseEntity<ResourceResponse> create(@Valid @RequestBody ResourceCreateRequest request) {
        ResourceResponse response = resourceService.create(request);
        URI location = URI.create("/api/v1/resources/" + response.id());
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ResourceResponse>> listAll() {
        return ResponseEntity.ok(resourceService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResourceResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(resourceService.getById(id));
    }
}
