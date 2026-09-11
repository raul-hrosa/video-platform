package com.videoplatform.provider.pulsertc;

import com.videoplatform.provider.ConnectionQuality;

/**
 * PulseRTC Quality -> Platform {@link ConnectionQuality} (Sprint 11 §11).
 * Nao inventa metricas: rating ausente ou desconhecido vira {@code UNKNOWN}.
 */
public final class PulseRtcQualityMapper {

    private PulseRtcQualityMapper() {
    }

    public static ConnectionQuality fromRating(String rating) {
        if (rating == null || rating.isBlank()) {
            return ConnectionQuality.UNKNOWN;
        }
        return switch (rating.trim().toLowerCase()) {
            case "excellent" -> ConnectionQuality.EXCELLENT;
            case "good" -> ConnectionQuality.GOOD;
            case "unstable", "warning", "fair" -> ConnectionQuality.UNSTABLE;
            case "poor", "bad", "critical" -> ConnectionQuality.POOR;
            default -> ConnectionQuality.UNKNOWN;
        };
    }
}
