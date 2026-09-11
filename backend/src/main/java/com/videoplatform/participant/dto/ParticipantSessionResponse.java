package com.videoplatform.participant.dto;

import com.videoplatform.participant.ParticipantSession;

import java.time.Instant;
import java.util.UUID;

public record ParticipantSessionResponse(
        UUID sessionId,
        String participantId,
        String participantName,
        Instant joinedAt,
        Instant leftAt,
        Long durationSeconds,
        int reconnectCount
) {

    public static ParticipantSessionResponse from(ParticipantSession session) {
        return new ParticipantSessionResponse(
                session.getId(),
                session.getParticipantId(),
                session.getParticipantName(),
                session.getJoinedAt(),
                session.getLeftAt(),
                session.getDurationSeconds(),
                session.getReconnectCount());
    }
}
