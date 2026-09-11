package com.videoplatform.provider;

import java.time.Instant;

/**
 * Evento de webhook do provider de midia ja traduzido para o modelo da
 * plataforma. Os controllers/servicos de dominio trabalham apenas com este
 * record; nenhuma classe do SDK LiveKit atravessa esta fronteira (Sprint 10 §20).
 *
 * <p>Campos de participante ficam {@code null} para eventos de sala.
 */
public record MediaWebhookEvent(
        Type type,
        String rawType,
        String eventId,
        Instant occurredAt,
        String roomId,
        String participantId,
        String participantName,
        Instant participantJoinedAt) {

    public enum Type {
        ROOM_STARTED,
        ROOM_FINISHED,
        PARTICIPANT_JOINED,
        PARTICIPANT_LEFT,
        /** Qualquer evento que a plataforma ainda nao trata como efeito de dominio. */
        OTHER
    }
}
