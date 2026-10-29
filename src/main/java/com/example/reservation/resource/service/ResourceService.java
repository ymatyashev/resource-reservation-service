package com.example.reservation.resource.service;

import com.example.reservation.common.exception.ResourceNotFoundException;
import com.example.reservation.resource.dto.ResourceCreateRequest;
import com.example.reservation.resource.dto.ResourceResponse;
import com.example.reservation.resource.entity.Resource;
import com.example.reservation.resource.repository.ResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ResourceService {

    private final ResourceRepository resourceRepository;

    public ResourceService(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    @Transactional
    public ResourceResponse create(ResourceCreateRequest request) {
        Resource resource = new Resource(request.getName());
        Resource saved = resourceRepository.save(resource);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> listAll() {
        return resourceRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResourceResponse getById(UUID id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + id));
        return toResponse(resource);
    }

    @Transactional(readOnly = true)
    public Resource getByIdEntity(UUID id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + id));
    }

    @Transactional
    public Resource getByIdEntityForUpdate(UUID id) {
        return resourceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + id));
    }

    private ResourceResponse toResponse(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getName(),
                resource.isActive(),
                resource.getCreatedAt()
        );
    }
}
