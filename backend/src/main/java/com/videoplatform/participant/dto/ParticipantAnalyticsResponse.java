package com.videoplatform.participant.dto;

import java.time.Instant;
import java.util.List;

/**
 * Visao detalhada de um participante numa sala (Sprint 9 §11). So dados
 * persistidos — campos sem medicao vem {@code null}, nunca inventados (§22).
 */
public record ParticipantAnalyticsResponse(
        String participantRef,
        String kind,
        String displayName,
        CurrentSession currentSession,
        History history,
        Quality quality,
        List<SessionEntry> sessions
) {

    public record CurrentSession(java.util.UUID sessionId, Instant joinedAt, long connectedSeconds) {
    }

    public record History(int totalSessions, long totalConnectedSeconds, int reconnections) {
    }

    /** {@code null} quando o participante nunca enviou snapshot de qualidade. */
    public record Quality(String average, String current) {
    }

    public record SessionEntry(java.util.UUID sessionId, Instant joinedAt, Instant leftAt,
                               Long durationSeconds, int reconnectCount) {
    }
}
