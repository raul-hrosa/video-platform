package com.videoplatform.roomprofile.dto;

import com.videoplatform.roomprofile.RoomProfile;

import java.util.List;

/**
 * Envelope {@code { "content": [...] }} — mesmo shape das listagens paginadas,
 * mas sem paginacao real nesta sprint (Sprint 5 §12).
 */
public record RoomProfileListResponse(List<RoomProfileResponse> content) {

    public static RoomProfileListResponse of(List<RoomProfile> profiles) {
        return new RoomProfileListResponse(
                profiles.stream().map(RoomProfileResponse::from).toList());
    }
}
