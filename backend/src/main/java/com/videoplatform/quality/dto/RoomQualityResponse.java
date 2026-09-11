package com.videoplatform.quality.dto;

import java.time.Instant;
import java.util.List;

/**
 * Qualidade de conexão de uma sala, por participante (Sprint 6 §21-24).
 *
 * <p>Só usa dados persistidos. {@code hasData=false} quando <b>nenhum</b>
 * participante enviou métrica — o frontend mostra "sem dados de qualidade", nunca
 * um gráfico inventado (§23). Quando ao menos um participante tem dados, a lista
 * traz <b>todos</b> os participantes; os sem medição (ex.: convidados) vêm com
 * {@code latestLevel} e métricas {@code null}. {@code timelineAvailable} indica
 * se há histórico temporal suficiente (≥2 snapshots em algum participante) para
 * um gráfico de evolução; caso contrário, mostrar só o resumo final.
 *
 * <p>A classificação é a mesma da Sprint 3 ({@code QualityLevel}) — não há uma
 * segunda escala (§21, §25).
 */
public record RoomQualityResponse(
        boolean hasData,
        boolean timelineAvailable,
        List<ParticipantQuality> participants
) {

    /**
     * Última leitura de qualidade de um participante. {@code latestLevel} é
     * {@code null} quando o participante não enviou nenhuma métrica ("sem
     * medição"). Métricas ausentes ficam {@code null} — nunca 0, nunca inferidas (§22).
     */
    public record ParticipantQuality(
            String participantId,
            String participantName,
            boolean isGuest,
            String latestLevel,
            Integer rttMs,
            Double packetLossPercent,
            Integer jitterMs,
            Instant lastRecordedAt,
            int snapshots
    ) {
    }
}
