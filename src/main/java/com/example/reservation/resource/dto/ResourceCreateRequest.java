package com.example.reservation.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResourceCreateRequest {

    @NotBlank(message = "Resource name must not be blank")
    @Size(max = 255, message = "Resource name must not exceed 255 characters")
    private String name;

    public ResourceCreateRequest() {
    }

    public ResourceCreateRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
