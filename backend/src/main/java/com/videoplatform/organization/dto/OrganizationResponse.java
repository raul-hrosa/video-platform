package com.videoplatform.organization.dto;

import com.videoplatform.organization.Organization;
import com.videoplatform.organization.OrgRole;

import java.util.UUID;

/** {@code GET/PUT /api/v1/organizations/current} (Sprint 7 §28, §29). */
public record OrganizationResponse(UUID id, String name, String slug, OrgRole role) {

    public static OrganizationResponse from(Organization org, OrgRole role) {
        return new OrganizationResponse(org.getId(), org.getName(), org.getSlug(), role);
    }
}
