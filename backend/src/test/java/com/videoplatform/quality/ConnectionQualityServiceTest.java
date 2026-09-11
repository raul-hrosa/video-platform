package com.videoplatform.quality;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionQualityServiceTest {

    private final ConnectionQualityService service = new ConnectionQualityService();

    @Test
    void classifiesExcellent() {
        assertThat(service.classify(50, 0.3, 8)).isEqualTo(QualityLevel.EXCELLENT);
    }

    @Test
    void classifiesGood() {
        assertThat(service.classify(120, 1.5, 20)).isEqualTo(QualityLevel.GOOD);
    }

    @Test
    void classifiesFair() {
        assertThat(service.classify(200, 3.0, 40)).isEqualTo(QualityLevel.FAIR);
    }

    @Test
    void classifiesPoor() {
        assertThat(service.classify(350, 8.0, 90)).isEqualTo(QualityLevel.POOR);
    }

    @Test
    void classifiesVeryPoor() {
        assertThat(service.classify(500, 15.0, 150)).isEqualTo(QualityLevel.VERY_POOR);
    }

    @Test
    void worstMetricWins() {
        // RTT excelente mas packet loss pessimo -> VERY_POOR
        assertThat(service.classify(50, 20.0, 5)).isEqualTo(QualityLevel.VERY_POOR);
    }

    @Test
    void boundaryValuesRoundDown() {
        // exatamente no limite do proximo nivel conta como o nivel de baixo
        assertThat(service.classify(80, null, null)).isEqualTo(QualityLevel.GOOD);
        assertThat(service.classify(null, 1.0, null)).isEqualTo(QualityLevel.GOOD);
    }

    @Test
    void missingMetricsDefaultToFair() {
        assertThat(service.classify(null, null, null)).isEqualTo(QualityLevel.FAIR);
    }

    @Test
    void jitterAloneDoesNotDropBelowFair() {
        assertThat(service.classify(null, null, 500)).isEqualTo(QualityLevel.FAIR);
    }

    @Test
    void jitterCombinesWithOtherMetrics() {
        // RTT good, jitter very poor -> jitter conta e puxa para baixo
        assertThat(service.classify(100, null, 200)).isEqualTo(QualityLevel.VERY_POOR);
    }
}
