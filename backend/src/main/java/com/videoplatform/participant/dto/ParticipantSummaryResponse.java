package com.videoplatform.participant.dto;

import java.time.Instant;
import java.util.List;

/**
 * Resumo agregado por participante de uma sala (Sprint 6 §26-30). Uma pessoa que
 * entrou, saiu e voltou aparece <b>uma vez</b> aqui (participante distinto), com
 * {@code sessions > 1}. {@code participants} distintos != {@code entradas}.
 *
 * @param participantId   identidade LiveKit ({@code user:...} ou {@code guest:...})
 * @param participantName nome exibível
 * @param isGuest         entrou sem conta
 * @param sessions        nº de participações concretas (= entradas)
 * @param completedSessions sessões já encerradas
 * @param reconnects      soma das reconexões em todas as sessões
 * @param totalDurationSeconds soma da duração de todas as sessões (sessões abertas contam até agora)
 * @param firstJoinedAt   entrada mais antiga
 * @param lastLeftAt      saída mais recente; {@code null} se ainda há sessão aberta
 */
public record ParticipantSummaryResponse(
        String participantId,
        String participantName,
        boolean isGuest,
        int sessions,
        int completedSessions,
        int reconnects,
        long totalDurationSeconds,
        Instant firstJoinedAt,
        Instant lastLeftAt
) {

    public record RoomParticipantsSummary(
            int distinctParticipants,
            int totalSessions,
            int totalEntries,
            long totalParticipantSeconds,
            List<ParticipantSummaryResponse> participants
    ) {
    }
}
