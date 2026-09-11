package com.videoplatform.room.dto;

import com.videoplatform.room.Room;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma sala na API. No histórico (Sprint 6 §10) vem acompanhada do nome/tipo do
 * Room Profile de origem — resolvidos em lote pelo serviço, sem N+1. A partir da
 * Sprint 7 traz também {@code ownerName} ("Criada por João" — §42), já que uma
 * Organization tem vários criadores. Campos não carregados ficam {@code null}.
 */
public record RoomResponse(
        UUID id,
        String roomId,
        String name,
        String status,
        String displayStatus,
        String creationMode,
        UUID roomProfileId,
        String roomProfileName,
        String roomProfileType,
        UUID ownerId,
        String ownerName,
        Integer durationMinutes,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        Instant expiresAt
) {

    public static RoomResponse from(Room room) {
        return from(room, null, null, null);
    }

    public static RoomResponse from(Room room, String roomProfileName, String roomProfileType,
                                    String ownerName) {
        return new RoomResponse(
                room.getId(),
                room.getRoomId(),
                room.getName(),
                room.getStatus().name(),
                room.displayStatus(),
                room.creationMode().name(),
                room.getRoomProfileId(),
                roomProfileName,
                roomProfileType,
                room.getOwnerId(),
                ownerName,
                room.getDurationMinutes(),
                room.getCreatedAt(),
                room.getStartedAt(),
                room.getEndedAt(),
                room.getExpiresAt());
    }
}
