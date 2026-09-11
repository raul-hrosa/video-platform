package com.videoplatform.participant.dto;

import java.time.Instant;

/**
 * Uma identidade de participante numa sala, com os totais agregados das suas
 * {@code ParticipantSession}s (Sprint 9 §10). Substitui a antiga listagem de
 * sessoes em {@code GET /api/v1/rooms/{roomId}/participants} — as sessoes
 * individuais continuam em {@code GET .../sessions}.
 */
public record RoomParticipantResponse(
        String participantRef,
        String kind,
        String displayName,
        int totalSessions,
        long totalConnectedSeconds,
        int reconnections,
        boolean currentSessionOpen,
        Instant firstSeenAt,
        Instant lastSeenAt,
        String latestQualityLevel
) {
}
