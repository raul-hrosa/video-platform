package com.videoplatform.provider;

import com.videoplatform.quality.QualityLevel;

/** Mapeia escalas legadas/persistidas para o modelo interno da plataforma. */
public final class QualityMapper {

    private QualityMapper() {
    }

    public static ConnectionQuality fromLegacy(QualityLevel level) {
        if (level == null) {
            return ConnectionQuality.UNKNOWN;
        }
        return switch (level) {
            case EXCELLENT -> ConnectionQuality.EXCELLENT;
            case GOOD -> ConnectionQuality.GOOD;
            case FAIR -> ConnectionQuality.UNSTABLE;
            case POOR, VERY_POOR -> ConnectionQuality.POOR;
        };
    }
}
