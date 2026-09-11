package com.videoplatform.quality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Classifica a qualidade de conexao a partir de RTT, packet loss e jitter.
 *
 * <p>Regra "pior metrica vence" (Sprint 3 §37): calcula um nivel por metrica
 * disponivel e devolve o pior. Metricas ausentes sao ignoradas.
 *
 * <p>Os limites abaixo sao <b>heuristicas iniciais</b> — nao um padrao universal
 * de qualidade de internet. Devem poder ser ajustados.
 */
@Service
public class ConnectionQualityService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionQualityService.class);

    // limite superior (exclusivo) de cada nivel; acima do ultimo => VERY_POOR
    private static final int[] RTT_MS = {80, 150, 250, 400};
    private static final double[] LOSS_PCT = {1.0, 2.0, 5.0, 10.0};
    private static final int[] JITTER_MS = {15, 30, 50, 100};

    private static final QualityLevel[] BY_INDEX = {
            QualityLevel.EXCELLENT, QualityLevel.GOOD, QualityLevel.FAIR,
            QualityLevel.POOR, QualityLevel.VERY_POOR
    };

    public QualityLevel classify(Integer rttMs, Double packetLossPercent, Integer jitterMs) {
        QualityLevel worst = null;

        if (rttMs != null) {
            worst = worst(worst, levelFor(rttMs, RTT_MS));
        }
        if (packetLossPercent != null) {
            worst = worst(worst, levelFor(packetLossPercent, LOSS_PCT));
        }
        if (jitterMs != null) {
            // jitter e' complementar: sozinho nao rebaixa abaixo de FAIR
            QualityLevel jitterLevel = levelFor(jitterMs, JITTER_MS);
            if (rttMs == null && packetLossPercent == null
                    && jitterLevel.ordinal() < QualityLevel.FAIR.ordinal()) {
                jitterLevel = QualityLevel.FAIR;
            }
            worst = worst(worst, jitterLevel);
        }

        if (worst == null) {
            log.atWarn().addKeyValue("event", "CONNECTION_QUALITY_NO_METRICS")
                    .setMessage("no metrics available to classify, defaulting to FAIR").log();
            return QualityLevel.FAIR;
        }
        return worst;
    }

    private static QualityLevel levelFor(double value, double[] thresholds) {
        for (int i = 0; i < thresholds.length; i++) {
            if (value < thresholds[i]) {
                return BY_INDEX[i];
            }
        }
        return QualityLevel.VERY_POOR;
    }

    private static QualityLevel levelFor(int value, int[] thresholds) {
        for (int i = 0; i < thresholds.length; i++) {
            if (value < thresholds[i]) {
                return BY_INDEX[i];
            }
        }
        return QualityLevel.VERY_POOR;
    }

    private static QualityLevel worst(QualityLevel a, QualityLevel b) {
        if (a == null) {
            return b;
        }
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
