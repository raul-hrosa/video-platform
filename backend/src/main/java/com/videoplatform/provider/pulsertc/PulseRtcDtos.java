package com.videoplatform.provider.pulsertc;

import java.time.Instant;
import java.util.List;

/**
 * Modelos de transporte do control plane do PulseRTC ({@code /v1}). Ficam
 * confinados ao pacote {@code provider.pulsertc}: o dominio nunca os importa
 * (Sprint 11 §1/§22). O mapeamento para o modelo da plataforma acontece no
 * {@link PulseRtcMediaProvider} e no {@link PulseRtcQualityMapper}.
 */
final class PulseRtcDtos {

    private PulseRtcDtos() {
    }

    record CreateRoomRequest(String roomId) {
    }

    record RoomView(String roomId, String status) {
    }

    record TokenPermissions(boolean join, boolean publish, boolean subscribe, boolean control) {
        static TokenPermissions participant() {
            return new TokenPermissions(true, true, true, false);
        }
    }

    record CreateTokenRequest(String identity, String name, TokenPermissions permissions, long ttlSeconds) {
    }

    record TokenView(String token, String identity, String roomId, Instant expiresAt, String serverUrl) {
    }

    record ConnectionView(String roomId, String serverUrl, String token) {
    }

    record SessionView(String state, Integer generation, Boolean recoverable, String sessionId) {
    }

    record ParticipantView(String identity, String name, String state, SessionView session) {
    }

    record ParticipantListView(List<ParticipantView> participants) {
    }

    /**
     * Veredito de um stream/participante (a Quality Engine do PulseRTC calcula).
     * {@code metrics} é um mapa livre com o que o PulseRTC expõe (packetLossPct,
     * jitterMs, rttMs, bitrateBps, fps…) — só para o modo diagnóstico do frontend.
     */
    record QualityVerdict(String status, Integer score, String reason, java.util.Map<String, Object> metrics) {
    }

    /** GET /v1/rooms/{id}/participants/{identity}/quality */
    record ParticipantQualityView(
            String identity,
            String status,
            Integer score,
            String reason,
            java.util.Map<String, Object> metrics,
            QualityVerdict audio,
            QualityVerdict video,
            QualityVerdict connection) {
    }

    /** GET /v1/rooms/{id}/quality */
    record RoomQualityView(List<ParticipantQualityView> participants) {
    }

    /** Compat: mantido para o {@code getRoomQuality} legado do provider. */
    record QualityView(String overall) {
    }

    /** Corpo de erro padrao do PulseRTC. */
    record ErrorBody(String code, String message, String requestId) {
    }
}
