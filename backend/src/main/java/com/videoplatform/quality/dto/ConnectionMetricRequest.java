package com.videoplatform.quality.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Snapshot de qualidade enviado pelo cliente. Metricas ausentes devem vir como
 * {@code null} (nunca 0). O backend nunca deve receber token/credenciais aqui.
 * A identidade vem do usuario autenticado — {@code participantId} e' apenas
 * informativo (opcional) e nao e' usado para autorizacao.
 */
public record ConnectionMetricRequest(
        @Size(max = 255) String participantId,

        @PositiveOrZero Integer rttMs,

        @DecimalMin("0.0") @DecimalMax("100.0") Double packetLossPercent,

        @PositiveOrZero Integer jitterMs,

        @PositiveOrZero Long audioBitrate,
        @PositiveOrZero Long videoBitrate,
        @PositiveOrZero Integer videoWidth,
        @PositiveOrZero Integer videoHeight,
        @PositiveOrZero Integer videoFps,

        @Size(max = 32) String connectionState,

        /** Dica do cliente; o backend reclassifica a partir das metricas. */
        @Size(max = 16) String qualityLevel,

        /** Contagem de reconexoes observada pelo cliente (inclui "soft"). */
        @PositiveOrZero Integer reconnectCount
) {
}
