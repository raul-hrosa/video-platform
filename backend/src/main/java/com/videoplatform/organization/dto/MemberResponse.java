package com.videoplatform.organization.dto;

import com.videoplatform.organization.OrgRole;

import java.time.Instant;
import java.util.UUID;

/** Um membro em {@code GET /api/v1/organizations/current/members} (Sprint 7 §30). */
public record MemberResponse(
        UUID userId,
        String name,
        String email,
        OrgRole role,
        Instant memberSince
) {
}
