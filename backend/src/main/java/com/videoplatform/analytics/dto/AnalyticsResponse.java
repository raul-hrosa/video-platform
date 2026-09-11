package com.videoplatform.analytics.dto;

import java.util.Map;

public record AnalyticsResponse(
        String roomId,
        Long durationSeconds,
        long participants,
        long peakParticipants,
        long totalParticipantSeconds,
        QualityAnalytics quality
) {

    /**
     * Agregados de qualidade de conexao da sala. {@code null} quando a sala nao
     * tem nenhuma metrica registrada.
     *
     * @param levelDistribution fracao de snapshots por nivel (soma ~1.0)
     */
    public record QualityAnalytics(
            Integer avgRttMs,
            Double avgPacketLossPercent,
            long totalReconnects,
            Map<String, Double> levelDistribution
    ) {
    }
}
