package com.videoplatform.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code PUT /api/v1/organizations/current} (Sprint 7 §29). */
public record UpdateOrganizationRequest(
        @NotBlank @Size(max = 120) String name
) {
}
