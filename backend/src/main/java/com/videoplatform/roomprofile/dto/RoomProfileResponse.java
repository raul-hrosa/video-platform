package com.videoplatform.roomprofile.dto;

import com.videoplatform.roomprofile.RoomProfile;

import java.time.Instant;
import java.util.UUID;

/** Nunca inclui {@code ownerId} nem dados do {@code User} (Sprint 5 §11, §12). */
public record RoomProfileResponse(
        UUID id,
        String name,
        int durationMinutes,
        String type,
        Instant createdAt,
        Instant updatedAt
) {

    public static RoomProfileResponse from(RoomProfile profile) {
        return new RoomProfileResponse(
                profile.getId(),
                profile.getName(),
                profile.getDurationMinutes(),
                profile.getType().name(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
